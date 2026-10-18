package com.eduerp.service;

import com.eduerp.dto.FeePaymentDTO;
import com.eduerp.dto.FeeStructureDTO;
import com.eduerp.entity.*;
import com.eduerp.exception.ResourceNotFoundException;
import com.eduerp.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FeeService {
    private final FeeStructureRepository structureRepository;
    private final FeeInvoiceRepository invoiceRepository;
    private final FeePaymentRepository paymentRepository;
    private final CourseRepository courseRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;

    @Transactional
    public FeeStructureDTO createStructure(FeeStructureDTO dto, Long adminId) {
        validateStructure(dto);
        Course course = dto.getCourseId() == null ? null : courseRepository.findById(dto.getCourseId())
                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));
        User admin = userRepository.findById(adminId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        FeeStructure saved = structureRepository.save(FeeStructure.builder().category(dto.getCategory().trim())
                .amount(dto.getAmount()).dueDate(dto.getDueDate()).semester(dto.getSemester())
                .academicYear(dto.getAcademicYear()).allowPartial(Boolean.TRUE.equals(dto.getAllowPartial())).course(course).createdBy(admin).build());
        return mapStructure(saved);
    }

    @Transactional(readOnly = true)
    public List<FeeStructureDTO> getStructures() { return structureRepository.findAll().stream().map(this::mapStructure).toList(); }

    @Transactional(readOnly = true)
    public List<FeeStructureDTO> getApplicableStructures(Long studentId) {
        Student student = studentRepository.findById(studentId).orElseThrow(() -> new ResourceNotFoundException("Student not found"));
        return structureRepository.findAll().stream().filter(s -> (s.getCourse() == null || student.getCourses().contains(s.getCourse()))
                && (s.getSemester() == null || s.getSemester().equals(student.getSemester())))
                .map(this::mapStructure).toList();
    }

    @Transactional
    public FeePaymentDTO createInvoice(Long studentId, Long structureId, BigDecimal amount, Long adminId) {
        Student student = studentRepository.findById(studentId).orElseThrow(() -> new ResourceNotFoundException("Student not found"));
        FeeStructure structure = structureRepository.findById(structureId).orElseThrow(() -> new ResourceNotFoundException("Fee structure not found"));
        if (invoiceRepository.existsByStudentIdAndFeeStructureId(studentId, structureId)) {
            throw new IllegalArgumentException("Fee invoice already exists for this student and structure");
        }
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("Invoice amount must be positive");
        User admin = userRepository.findById(adminId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        FeeInvoice invoice = invoiceRepository.save(FeeInvoice.builder().student(student).feeStructure(structure)
                .category(structure.getCategory()).amount(amount).dueDate(structure.getDueDate()).createdBy(admin).build());
        return mapInvoice(invoice);
    }

    @Transactional(readOnly = true)
    public List<FeePaymentDTO> getLedger(Long studentId) {
        return invoiceRepository.findByStudentId(studentId).stream().map(this::mapInvoice).toList();
    }

    @Transactional(readOnly = true)
    public List<FeePaymentDTO> getAllInvoices() { return invoiceRepository.findAll().stream().map(this::mapInvoice).toList(); }

    @Transactional
    public FeePaymentDTO recordPayment(Long invoiceId, BigDecimal amount, String reference, String notes, Long adminId) {
        if (amount == null || amount.signum() <= 0 || reference == null || reference.isBlank())
            throw new IllegalArgumentException("Positive amount and unique payment reference are required");
        if (paymentRepository.existsByReference(reference.trim())) throw new IllegalArgumentException("Payment reference already exists");
        FeeInvoice invoice = invoiceRepository.findById(invoiceId).orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
        BigDecimal balance = balance(invoice);
        if (!Boolean.TRUE.equals(invoice.getFeeStructure().getAllowPartial()) && amount.compareTo(balance) != 0) throw new IllegalArgumentException("This fee requires payment of the full balance");
        if (amount.compareTo(balance) > 0) throw new IllegalArgumentException("Payment exceeds invoice balance");
        User admin = userRepository.findById(adminId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        paymentRepository.save(FeePayment.builder().invoice(invoice).amount(amount).reference(reference.trim()).notes(notes).recordedBy(admin).status(FeePaymentStatus.CONFIRMED).confirmedAt(java.time.LocalDateTime.now()).idempotencyKey("manual-" + reference.trim()).build());
        return mapInvoice(invoice);
    }

    @Transactional
    public FeePaymentDTO initiatePayment(Long invoiceId, BigDecimal amount, String idempotencyKey, User studentUser) {
        if (amount == null || amount.signum() <= 0 || idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalArgumentException("Positive amount and idempotency key are required");
        FeeInvoice invoice = invoiceRepository.findById(invoiceId).orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
        if (!invoice.getStudent().getUser().getId().equals(studentUser.getId())) throw new org.springframework.security.access.AccessDeniedException("You may only pay your own invoice");
        if (!Boolean.TRUE.equals(invoice.getFeeStructure().getAllowPartial()) && amount.compareTo(balance(invoice)) != 0) throw new IllegalArgumentException("This fee does not allow partial payment");
        if (amount.compareTo(balance(invoice)) > 0) throw new IllegalArgumentException("Payment exceeds invoice balance");
        if (paymentRepository.existsByIdempotencyKey(idempotencyKey.trim())) return mapInvoice(invoice);
        FeePayment payment = FeePayment.builder().invoice(invoice).amount(amount).reference("PENDING-" + idempotencyKey.trim()).idempotencyKey(idempotencyKey.trim()).recordedBy(studentUser).status(FeePaymentStatus.PENDING).notes("Awaiting payment-provider confirmation; no funds recorded").build();
        paymentRepository.save(payment);
        return mapInvoice(invoice);
    }

    private void validateStructure(FeeStructureDTO dto) {
        if (dto.getCategory() == null || dto.getCategory().isBlank() || dto.getAmount() == null || dto.getAmount().signum() <= 0 || dto.getDueDate() == null)
            throw new IllegalArgumentException("Category, positive amount, and due date are required");
    }

    private FeeStructureDTO mapStructure(FeeStructure s) {
        FeeStructureDTO dto = new FeeStructureDTO(); dto.setId(s.getId()); dto.setCategory(s.getCategory()); dto.setAmount(s.getAmount());
        dto.setDueDate(s.getDueDate()); dto.setSemester(s.getSemester()); dto.setAcademicYear(s.getAcademicYear());
        dto.setCourseId(s.getCourse() == null ? null : s.getCourse().getId()); dto.setAllowPartial(s.getAllowPartial()); return dto;
    }

    private FeePaymentDTO mapInvoice(FeeInvoice invoice) {
        List<FeePaymentDTO.PaymentRecord> payments = paymentRepository.findByInvoiceIdOrderByPaidAtDesc(invoice.getId()).stream()
                .map(p -> new FeePaymentDTO.PaymentRecord(p.getAmount(), p.getReference(), p.getPaidAt(), p.getNotes(), p.getStatus())).toList();
        BigDecimal paid = paymentRepository.findByInvoiceIdOrderByPaidAtDesc(invoice.getId()).stream().filter(p -> p.getStatus() == FeePaymentStatus.CONFIRMED).map(FeePayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        FeePaymentDTO dto = new FeePaymentDTO(); dto.setInvoiceId(invoice.getId()); dto.setStudentId(invoice.getStudent().getId());
        dto.setStudentName(invoice.getStudent().getUser().getFullName()); dto.setCategory(invoice.getCategory()); dto.setAmount(invoice.getAmount());
        dto.setAdjustment(invoice.getAdjustment()); dto.setPaid(paid); dto.setBalance(balance(invoice)); dto.setDueDate(invoice.getDueDate());
        dto.setAdjustmentReason(invoice.getAdjustmentReason()); dto.setPayments(payments); dto.setStatus(payments.stream().findFirst().map(FeePaymentDTO.PaymentRecord::status).orElse(null)); return dto;
    }

    private BigDecimal balance(FeeInvoice invoice) {
        BigDecimal paid = paymentRepository.findByInvoiceIdOrderByPaidAtDesc(invoice.getId()).stream().filter(p -> p.getStatus() == FeePaymentStatus.CONFIRMED).map(FeePayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return invoice.getAmount().add(invoice.getAdjustment()).subtract(paid).max(BigDecimal.ZERO);
    }
}
