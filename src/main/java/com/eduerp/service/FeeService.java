package com.eduerp.service;

import com.eduerp.dto.FeePaymentDTO;
import com.eduerp.dto.FeeStructureDTO;
import com.eduerp.dto.FinanceDashboardDTO;
import com.eduerp.entity.*;
import com.eduerp.exception.ConflictException;
import com.eduerp.exception.PaymentProviderException;
import com.eduerp.exception.ResourceNotFoundException;
import com.eduerp.repository.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FeeService {
    private static final Logger log = LoggerFactory.getLogger(FeeService.class);
    private final FeeStructureRepository structureRepository;
    private final FeeInvoiceRepository invoiceRepository;
    private final FeePaymentRepository paymentRepository;
    private final CourseRepository courseRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentWebhookEventRepository webhookEventRepository;
    private final ReceiptPdfService receiptPdfService;
    private final TaxRuleService taxRuleService;
    private final PaymentNotificationService paymentNotificationService;

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
    public FeePaymentDTO createInvoice(Long studentId, Long structureId, Long adminId) {
        User admin = administrator(adminId);
        Student student = studentRepository.findById(studentId).orElseThrow(() -> new ResourceNotFoundException("Student not found"));
        FeeStructure structure = structureRepository.findById(structureId).orElseThrow(() -> new ResourceNotFoundException("Fee structure not found"));
        if (invoiceRepository.existsByStudentIdAndFeeStructureId(studentId, structureId)) {
            throw new ConflictException("Fee invoice already exists for this student and structure");
        }
        BigDecimal amount = structure.getAmount();
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Fee structure amount must be positive");
        }
        TaxRuleService.TaxCalculation tax = taxRuleService.calculate(structure.getCategory(), amount, LocalDate.now());
        if (tax.total() == null || tax.total().signum() <= 0) {
            throw new IllegalArgumentException("Calculated invoice amount must be positive");
        }
        FeeInvoice invoice = invoiceRepository.save(FeeInvoice.builder().student(student).feeStructure(structure)
                .category(structure.getCategory()).amount(tax.total()).subtotal(tax.subtotal()).taxTotal(tax.taxTotal()).dueDate(structure.getDueDate()).createdBy(admin).build());
        taxRuleService.saveSnapshots(invoice, tax);
        return mapInvoice(invoice);
    }

    @Transactional(readOnly = true)
    public List<FeePaymentDTO> getLedger(Long studentId) {
        return invoiceRepository.findByStudentId(studentId).stream().map(this::mapInvoice).toList();
    }

    @Transactional(readOnly = true)
    public List<FeePaymentDTO> getAllInvoices() { return invoiceRepository.findAll().stream().map(this::mapInvoice).toList(); }

    @Transactional(readOnly = true)
    public FinanceDashboardDTO getFinanceDashboard() {
        LocalDate today = LocalDate.now();
        BigDecimal assigned = BigDecimal.ZERO;
        BigDecimal received = BigDecimal.ZERO;
        BigDecimal pending = BigDecimal.ZERO;
        BigDecimal overdue = BigDecimal.ZERO;
        Map<Long, StudentTotals> byStudent = new LinkedHashMap<>();

        for (FeeInvoice invoice : invoiceRepository.findAll()) {
            BigDecimal assessed = invoice.getAmount().add(invoice.getAdjustment());
            BigDecimal paid = confirmedPaid(invoice);
            BigDecimal balance = assessed.subtract(paid).max(BigDecimal.ZERO);
            BigDecimal invoiceOverdue = balance.signum() > 0 && invoice.getDueDate().isBefore(today)
                    ? balance : BigDecimal.ZERO;

            assigned = assigned.add(assessed);
            received = received.add(paid);
            pending = pending.add(balance);
            overdue = overdue.add(invoiceOverdue);

            Student student = invoice.getStudent();
            StudentTotals totals = byStudent.computeIfAbsent(student.getId(), ignored ->
                    new StudentTotals(student.getUser().getFullName()));
            totals.assessed = totals.assessed.add(assessed);
            totals.paid = totals.paid.add(paid);
            totals.pending = totals.pending.add(balance);
            totals.overdue = totals.overdue.add(invoiceOverdue);
        }

        List<FinanceDashboardDTO.StudentFinanceSummary> students = byStudent.entrySet().stream()
                .map(entry -> new FinanceDashboardDTO.StudentFinanceSummary(entry.getKey(), entry.getValue().name,
                        entry.getValue().assessed, entry.getValue().paid, entry.getValue().pending,
                        entry.getValue().overdue))
                .toList();
        return new FinanceDashboardDTO(assigned, students.size(), received, pending, overdue, students);
    }

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
    public FeePaymentDTO initiatePayment(Long invoiceId, BigDecimal requestedAmount,
                                         String idempotencyKey, User studentUser) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalArgumentException("Idempotency key is required");
        if (requestedAmount == null || requestedAmount.signum() <= 0) throw new IllegalArgumentException("Payment amount must be positive");
        BigDecimal amount;
        try { amount = requestedAmount.setScale(2, java.math.RoundingMode.UNNECESSARY); }
        catch (ArithmeticException ex) { throw new IllegalArgumentException("Payment amount may have at most two decimal places"); }
        FeeInvoice invoice = invoiceRepository.findByIdForUpdate(invoiceId).orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
        if (!invoice.getStudent().getUser().getId().equals(studentUser.getId())) throw new AccessDeniedException("You may only pay your own invoice");
        String normalizedIdempotencyKey = idempotencyKey.trim();
        FeePayment existing = paymentRepository.findByIdempotencyKey(normalizedIdempotencyKey).orElse(null);
        if (existing != null) {
            if (!existing.getInvoice().getId().equals(invoiceId)) throw new ConflictException("Idempotency key is already assigned to another invoice");
            return checkoutDetails(mapInvoice(invoice), existing.getAmount(), paymentGateway.publicKeyId(), "INR");
        }
        FeePayment pendingForAmount = paymentRepository.findByInvoiceIdOrderByPaidAtDesc(invoiceId).stream()
                .filter(p -> p.getStatus() == FeePaymentStatus.PENDING
                        && p.getProviderOrderId() != null
                        && p.getAmount().compareTo(amount) == 0)
                .findFirst().orElse(null);
        if (pendingForAmount != null) {
            PaymentGateway.OrderCheck check = paymentGateway.checkOrder(pendingForAmount.getProviderOrderId(), pendingForAmount.getAmount());
            if (check.paid() && check.paymentId() != null) {
                log.info("Pending order {} was already paid in Razorpay; reconciling: paymentId={}",
                        pendingForAmount.getProviderOrderId(), check.paymentId());
                return confirmPaymentInternal(pendingForAmount, check.paymentId(), null, studentUser);
            }
            if (check.usable()) return checkoutDetails(mapInvoice(invoice), pendingForAmount.getAmount(), paymentGateway.publicKeyId(), "INR");
            if (check.definitive()) {
                pendingForAmount.setStatus(FeePaymentStatus.FAILED);
                pendingForAmount.setFailureCode("PROVIDER_ORDER_INVALID");
                paymentRepository.save(pendingForAmount);
            }
        }
        BigDecimal outstanding = balance(invoice);
        BigDecimal pending = pendingPayments(invoice);
        BigDecimal available = outstanding.subtract(pending).max(BigDecimal.ZERO);
        log.info("Fee payment validation: phase=initiate invoiceId={} studentId={} requestedAmount={} unit=INR invoiceAmount={} confirmedPaid={} outstanding={} pendingReserved={} available={} comparison=requested>available result={}",
                invoice.getId(), studentUser.getId(), amount, invoice.getAmount(), confirmedPaid(invoice), outstanding,
                pending, available, amount.compareTo(available) > 0);
        if (amount.compareTo(available) > 0) {
            log.warn("Fee payment rejected: phase=initiate invoiceId={} studentId={} reason=REQUEST_EXCEEDS_AVAILABLE requestedAmount={} available={}",
                    invoice.getId(), studentUser.getId(), amount, available);
            throw new IllegalArgumentException("Payment exceeds the latest outstanding balance");
        }
        PaymentGateway.PaymentOrder order = paymentGateway.createOrder(invoiceId, amount, normalizedIdempotencyKey, "EDUERP-INV-" + invoiceId);
        FeePayment payment = FeePayment.builder().invoice(invoice).amount(amount).reference("PENDING-" + idempotencyKey.trim())
                .idempotencyKey(normalizedIdempotencyKey).recordedBy(studentUser).status(FeePaymentStatus.PENDING)
                .provider(order.provider()).providerOrderId(order.orderId())
                .notes("Awaiting verified payment-provider confirmation; no funds recorded").build();
        paymentRepository.save(payment);
        return checkoutDetails(mapInvoice(invoice), order.amount(), order.keyId(), order.currency());
    }

    private FeePaymentDTO checkoutDetails(FeePaymentDTO dto, BigDecimal amount, String keyId, String currency) {
        if (keyId == null || keyId.isBlank()) {
            throw new PaymentProviderException("Razorpay credentials are not configured", 503, "CONFIGURATION_MISSING",
                    new IllegalStateException("Razorpay public key is missing"));
        }
        dto.setProviderKeyId(keyId);
        dto.setProviderAmount(amount);
        dto.setProviderCurrency(currency);
        return dto;
    }

    @Transactional
    public FeePaymentDTO confirmRazorpayPayment(String orderId, String paymentId, String signature, User studentUser) {
        if (orderId == null || orderId.isBlank() || paymentId == null || paymentId.isBlank()
                || signature == null || signature.isBlank()) {
            throw new IllegalArgumentException("Payment confirmation details are required");
        }
        paymentGateway.verifyPaymentSignature(orderId, paymentId, signature);
        FeePayment payment = paymentRepository.findByProviderOrderIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment order not found"));
        return confirmPaymentInternal(payment, paymentId, signature, studentUser);
    }

    @Transactional
    public FeePaymentDTO reconcileStudentOrder(String orderId, User studentUser) {
        if (orderId == null || orderId.isBlank()) throw new IllegalArgumentException("Order ID is required");
        FeePayment payment = paymentRepository.findByProviderOrderIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment order not found"));
        if (!payment.getInvoice().getStudent().getUser().getId().equals(studentUser.getId())) {
            throw new AccessDeniedException("You may only reconcile your own payment");
        }
        if (payment.getStatus() == FeePaymentStatus.CONFIRMED) {
            return mapInvoice(payment.getInvoice());
        }
        PaymentGateway.OrderCheck check = paymentGateway.checkOrder(orderId, payment.getAmount());
        if (check.paid() && check.paymentId() != null) {
            return confirmPaymentInternal(payment, check.paymentId(), null, studentUser);
        }
        throw new IllegalStateException("Order " + orderId + " is not reported as paid by the payment provider");
    }

    @Transactional
    public FeePaymentDTO reconcilePaymentOrder(String orderId, Long adminId) {
        administrator(adminId);
        if (orderId == null || orderId.isBlank()) throw new IllegalArgumentException("Order ID is required");
        FeePayment payment = paymentRepository.findByProviderOrderIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment order not found"));
        if (payment.getStatus() == FeePaymentStatus.CONFIRMED) {
            return mapInvoice(payment.getInvoice());
        }
        PaymentGateway.OrderCheck check = paymentGateway.checkOrder(orderId, payment.getAmount());
        if (check.paid() && check.paymentId() != null) {
            return confirmPaymentInternal(payment, check.paymentId(), null, payment.getInvoice().getStudent().getUser());
        }
        throw new IllegalStateException("Order " + orderId + " is not reported as paid by the payment provider");
    }

    private FeePaymentDTO confirmPaymentInternal(FeePayment payment, String paymentId, String signature, User studentUser) {
        if (!payment.getInvoice().getStudent().getUser().getId().equals(studentUser.getId())) {
            throw new AccessDeniedException("You may only confirm your own payment");
        }
        if (payment.getStatus() == FeePaymentStatus.CONFIRMED) {
            if (payment.getProviderPaymentId() != null && !paymentId.equals(payment.getProviderPaymentId())) {
                throw new ConflictException("This order is already linked to another payment");
            }
            return mapInvoice(payment.getInvoice());
        }
        paymentGateway.verifyPaymentDetails(payment.getProviderOrderId(), paymentId, payment.getAmount());
        if (paymentRepository.findByProviderPaymentId(paymentId).filter(existing -> !existing.getId().equals(payment.getId())).isPresent()) {
            throw new IllegalArgumentException("Payment has already been recorded");
        }
        FeeInvoice invoice = invoiceRepository.findByIdForUpdate(payment.getInvoice().getId()).orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
        BigDecimal outstanding = balance(invoice);
        BigDecimal pending = pendingPayments(invoice);
        log.info("Fee payment validation: phase=confirm invoiceId={} studentId={} requestedAmount={} unit=INR invoiceAmount={} confirmedPaid={} outstanding={} pendingReserved={} comparison=requested>outstanding result={}",
                invoice.getId(), studentUser.getId(), payment.getAmount(), invoice.getAmount(), confirmedPaid(invoice),
                outstanding, pending, payment.getAmount().compareTo(outstanding) > 0);
        if (payment.getAmount().compareTo(outstanding) > 0) {
            log.warn("Fee payment rejected: phase=confirm invoiceId={} studentId={} reason=CONFIRMED_PAYMENT_EXCEEDS_OUTSTANDING requestedAmount={} outstanding={}",
                    invoice.getId(), studentUser.getId(), payment.getAmount(), outstanding);
            payment.setStatus(FeePaymentStatus.FAILED);
            payment.setFailureCode("BALANCE_CHANGED");
            paymentRepository.save(payment);
            throw new ConflictException("Payment amount exceeds the current outstanding balance");
        }
        payment.setInvoice(invoice);
        payment.setProviderPaymentId(paymentId);
        if (signature != null && !signature.isBlank()) {
            payment.setProviderSignature(signature);
        }
        payment.setReference(paymentId);
        payment.setStatus(FeePaymentStatus.CONFIRMED);
        payment.setConfirmedAt(java.time.LocalDateTime.now());
        paymentRepository.save(payment);
        if (paymentNotificationService != null) paymentNotificationService.notifyVerifiedPayment(payment);
        return mapInvoice(payment.getInvoice());
    }

    @Transactional(readOnly = true)
    public String receipt(String paymentId, User studentUser) {
        FeePayment payment = paymentRepository.findByProviderPaymentId(paymentId)
                .filter(item -> item.getStatus() == FeePaymentStatus.CONFIRMED)
                .orElseThrow(() -> new ResourceNotFoundException("Confirmed payment not found"));
        if (!payment.getInvoice().getStudent().getUser().getId().equals(studentUser.getId())) {
            throw new AccessDeniedException("You may only download your own receipt");
        }
        FeeInvoice invoice = payment.getInvoice();
        return "EduERP PAYMENT RECEIPT\n\n"
                + "Payment reference: " + payment.getProviderPaymentId() + "\n"
                + "Invoice: " + invoice.getId() + "\n"
                + "Student: " + invoice.getStudent().getUser().getFullName() + "\n"
                + "Category: " + invoice.getCategory() + "\n"
                + "Amount paid: " + payment.getAmount() + "\n"
                + "Confirmed at: " + payment.getConfirmedAt() + "\n";
    }

    @Transactional(readOnly = true)
    public byte[] receiptPdf(String paymentId, User studentUser) {
        FeePayment payment = paymentRepository.findByProviderPaymentId(paymentId)
                .filter(item -> item.getStatus() == FeePaymentStatus.CONFIRMED)
                .orElseThrow(() -> new ResourceNotFoundException("Confirmed payment not found"));
        if (!payment.getInvoice().getStudent().getUser().getId().equals(studentUser.getId())) throw new AccessDeniedException("You may only download your own receipt");
        FeeInvoice invoice = payment.getInvoice();
        BigDecimal totalPaid = confirmedPaid(invoice);
        return receiptPdfService.renderFeeReceipt(invoice, payment, totalPaid.subtract(payment.getAmount()).max(BigDecimal.ZERO), totalPaid, balance(invoice));
    }

    @Transactional(readOnly = true)
    public byte[] receiptPdfForAdministrator(String paymentId, User administrator) {
        administrator(administrator.getId());
        FeePayment payment = paymentRepository.findByProviderPaymentId(paymentId)
                .filter(item -> item.getStatus() == FeePaymentStatus.CONFIRMED || item.getStatus() == FeePaymentStatus.REFUNDED)
                .orElseThrow(() -> new ResourceNotFoundException("Payment receipt not found"));
        FeeInvoice invoice = payment.getInvoice();
        BigDecimal totalPaid = confirmedPaid(invoice);
        return receiptPdfService.renderFeeReceipt(invoice, payment, totalPaid.subtract(payment.getAmount()).max(BigDecimal.ZERO), totalPaid, balance(invoice));
    }

    @Transactional
    public void handleRazorpayWebhook(String eventId, String orderId, String paymentId, String status) {
        handleRazorpayWebhook(eventId, orderId, paymentId, status, null);
    }

    @Transactional
    public void handleRazorpayWebhook(String eventId, String orderId, String paymentId, String status, BigDecimal providerAmount) {
        if (eventId == null || eventId.isBlank()) throw new IllegalArgumentException("Webhook event ID is required");
        if (webhookEventRepository.existsByProviderAndEventId("RAZORPAY", eventId)) return;
        FeePayment payment = paymentRepository.findByProviderOrderIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment order not found"));
        if ("captured".equalsIgnoreCase(status) || "paid".equalsIgnoreCase(status)) {
            if (payment.getStatus() == FeePaymentStatus.CONFIRMED) {
                webhookEventRepository.save(PaymentWebhookEvent.builder().provider("RAZORPAY").eventId(eventId).build());
                return;
            }
            if (paymentRepository.findByProviderPaymentId(paymentId).filter(existing -> !existing.getId().equals(payment.getId())).isPresent()) {
                webhookEventRepository.save(PaymentWebhookEvent.builder().provider("RAZORPAY").eventId(eventId).build());
                return;
            }
            if (providerAmount == null || providerAmount.compareTo(payment.getAmount()) != 0) throw new IllegalArgumentException("Webhook payment amount does not match the order");
            FeeInvoice invoice = invoiceRepository.findByIdForUpdate(payment.getInvoice().getId()).orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
            BigDecimal outstanding = balance(invoice);
            BigDecimal pending = pendingPayments(invoice);
            log.info("Fee payment validation: phase=webhook invoiceId={} studentId={} requestedAmount={} unit=INR invoiceAmount={} confirmedPaid={} outstanding={} pendingReserved={} comparison=requested>outstanding result={}",
                    invoice.getId(), payment.getInvoice().getStudent().getUser().getId(), payment.getAmount(), invoice.getAmount(),
                    confirmedPaid(invoice), outstanding, pending, payment.getAmount().compareTo(outstanding) > 0);
            if (payment.getAmount().compareTo(outstanding) > 0) {
                log.warn("Fee payment rejected: phase=webhook invoiceId={} studentId={} reason=WEBHOOK_PAYMENT_EXCEEDS_OUTSTANDING requestedAmount={} outstanding={}",
                        invoice.getId(), payment.getInvoice().getStudent().getUser().getId(), payment.getAmount(), outstanding);
                payment.setStatus(FeePaymentStatus.FAILED);
                payment.setFailureCode("BALANCE_CHANGED");
                paymentRepository.save(payment);
                throw new ConflictException("Payment amount exceeds the current outstanding balance");
            }
            payment.setInvoice(invoice);
            payment.setProviderPaymentId(paymentId);
            payment.setReference(paymentId);
            payment.setStatus(FeePaymentStatus.CONFIRMED);
            payment.setConfirmedAt(java.time.LocalDateTime.now());
        } else if ("failed".equalsIgnoreCase(status) && payment.getStatus() == FeePaymentStatus.PENDING) {
            payment.setStatus(FeePaymentStatus.FAILED);
        }
        paymentRepository.save(payment);
        webhookEventRepository.save(PaymentWebhookEvent.builder().provider("RAZORPAY").eventId(eventId).build());
        if (payment.getStatus() == FeePaymentStatus.CONFIRMED && paymentNotificationService != null) paymentNotificationService.notifyVerifiedPayment(payment);
    }

    @Transactional
    public FeePaymentDTO refund(Long paymentId, BigDecimal amount, String idempotencyKey) {
        FeePayment payment = paymentRepository.findById(paymentId).orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        if (payment.getStatus() != FeePaymentStatus.CONFIRMED || payment.getProviderPaymentId() == null) throw new IllegalArgumentException("Only confirmed provider payments can be refunded");
        BigDecimal refundable = payment.getAmount().subtract(payment.getRefundedAmount()).max(BigDecimal.ZERO);
        if (amount == null || amount.signum() <= 0 || amount.compareTo(refundable) > 0) throw new IllegalArgumentException("Refund amount exceeds refundable payment balance");
        paymentGateway.refund(payment.getProviderPaymentId(), amount, idempotencyKey);
        payment.setRefundedAmount(payment.getRefundedAmount().add(amount));
        if (payment.getRefundedAmount().compareTo(payment.getAmount()) >= 0) payment.setStatus(FeePaymentStatus.REFUNDED);
        return mapInvoice(payment.getInvoice());
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
        BigDecimal paid = confirmedPaid(invoice);
        FeePaymentDTO dto = new FeePaymentDTO(); dto.setInvoiceId(invoice.getId()); dto.setStudentId(invoice.getStudent().getId());
        dto.setStudentName(invoice.getStudent().getUser().getFullName()); dto.setCategory(invoice.getCategory()); dto.setAmount(invoice.getAmount());
        dto.setAdjustment(invoice.getAdjustment()); dto.setSubtotal(invoice.getSubtotal()); dto.setTaxTotal(invoice.getTaxTotal()); dto.setPaid(paid); dto.setBalance(balance(invoice)); dto.setDueDate(invoice.getDueDate());
        dto.setAdjustmentReason(invoice.getAdjustmentReason()); dto.setPayments(payments); dto.setStatus(payments.stream().findFirst().map(FeePaymentDTO.PaymentRecord::status).orElse(null));
        paymentRepository.findByInvoiceIdOrderByPaidAtDesc(invoice.getId()).stream().findFirst().ifPresent(payment -> {
            dto.setProvider(payment.getProvider()); dto.setProviderOrderId(payment.getProviderOrderId()); dto.setProviderPaymentId(payment.getProviderPaymentId());
        });
        return dto;
    }

    private BigDecimal balance(FeeInvoice invoice) {
        BigDecimal paid = confirmedPaid(invoice);
        return invoice.getAmount().add(invoice.getAdjustment()).subtract(paid).max(BigDecimal.ZERO);
    }

    private BigDecimal confirmedPaid(FeeInvoice invoice) {
        return paymentRepository.findByInvoiceIdOrderByPaidAtDesc(invoice.getId()).stream()
                .filter(p -> p.getStatus() == FeePaymentStatus.CONFIRMED || p.getStatus() == FeePaymentStatus.REFUNDED)
                .map(p -> p.getAmount().subtract(p.getRefundedAmount()).max(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal availableBalance(FeeInvoice invoice) {
        return balance(invoice).subtract(pendingPayments(invoice)).max(BigDecimal.ZERO);
    }

    private BigDecimal pendingPayments(FeeInvoice invoice) {
        return paymentRepository.findByInvoiceIdOrderByPaidAtDesc(invoice.getId()).stream()
                .filter(p -> p.getStatus() == FeePaymentStatus.PENDING)
                .map(FeePayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private FeePaymentDTO getLedgerEntry(Long invoiceId) {
        return invoiceRepository.findById(invoiceId).map(this::mapInvoice)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
    }

    private User administrator(Long id) {
        User user = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getRole() != Role.ADMIN && user.getRole() != Role.ADMINISTRATOR) {
            throw new AccessDeniedException("Administrator permission required");
        }
        return user;
    }

    private static final class StudentTotals {
        private final String name;
        private BigDecimal assessed = BigDecimal.ZERO;
        private BigDecimal paid = BigDecimal.ZERO;
        private BigDecimal pending = BigDecimal.ZERO;
        private BigDecimal overdue = BigDecimal.ZERO;

        private StudentTotals(String name) { this.name = name; }
    }
}
