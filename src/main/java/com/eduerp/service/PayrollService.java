package com.eduerp.service;

import com.eduerp.dto.*;
import com.eduerp.entity.*;
import com.eduerp.exception.ConflictException;
import com.eduerp.exception.ResourceNotFoundException;
import com.eduerp.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service @RequiredArgsConstructor
public class PayrollService {
    private final SalaryStructureRepository structureRepository;
    private final PayrollRecordRepository payrollRepository;
    private final TeacherRepository teacherRepository;
    private final UserRepository userRepository;
    @Autowired(required = false) private AuditService auditService;
    @Autowired(required = false) private ReceiptPdfService receiptPdfService;

    @Transactional
    public SalaryStructureDTO configure(SalaryStructureDTO dto, Long adminId) {
        User admin = administrator(adminId);
        validateStructure(dto);
        Teacher teacher = teacherRepository.findById(dto.getTeacherId()).orElseThrow(() -> new ResourceNotFoundException("Teacher not found"));
        BigDecimal basic = dto.getBasicSalary() == null ? zero(dto.getGrossSalary()) : dto.getBasicSalary();
        BigDecimal allowances = zero(dto.getAllowances());
        BigDecimal bonuses = zero(dto.getBonuses());
        BigDecimal gross = basic.add(allowances).add(bonuses).setScale(2, RoundingMode.HALF_UP);
        SalaryStructure saved = structureRepository.save(SalaryStructure.builder().teacher(teacher).basicSalary(basic)
                .grossSalary(gross).allowances(allowances).bonuses(bonuses).deductions(zero(dto.getDeductions()))
                .paymentFrequency(dto.getPaymentFrequency().trim()).effectiveDate(dto.getEffectiveDate()).configuredBy(admin).build());
        audit(admin, "SALARY_STRUCTURE_CONFIGURED", "SALARY_STRUCTURE", saved.getId(), "teacher=" + teacher.getId());
        return map(saved);
    }

    @Transactional(readOnly = true) public List<SalaryStructureDTO> allStructures() { return structureRepository.findAll().stream().map(this::map).toList(); }
    @Transactional(readOnly = true) public List<SalaryStructureDTO> myStructures(Long teacherId) { return structureRepository.findByTeacherIdOrderByEffectiveDateDesc(teacherId).stream().map(this::map).toList(); }

    @Transactional
    public PayrollRecordDTO generate(Long teacherId, Integer year, Integer month, Long adminId) {
        User admin = administrator(adminId);
        validatePeriod(year, month);
        if (payrollRepository.findByTeacherIdAndPayrollYearAndPayrollMonth(teacherId, year, month).isPresent()) throw new ConflictException("Payroll already exists for this teacher and period");
        Teacher teacher = teacherRepository.findById(teacherId).orElseThrow(() -> new ResourceNotFoundException("Teacher not found"));
        SalaryStructure structure = structureRepository.findByTeacherIdOrderByEffectiveDateDesc(teacherId).stream()
                .filter(item -> !item.getEffectiveDate().isAfter(LocalDate.of(year, month, 1))).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No effective salary structure exists for this period"));
        BigDecimal gross = structure.getBasicSalary().add(structure.getAllowances()).add(structure.getBonuses()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal net = gross.subtract(structure.getDeductions()).setScale(2, RoundingMode.HALF_UP);
        if (net.signum() < 0) throw new IllegalArgumentException("Deductions cannot exceed gross salary");
        PayrollRecord record = payrollRepository.save(PayrollRecord.builder().teacher(teacher).salaryStructure(structure)
                .payrollYear(year).payrollMonth(month).basicSalary(structure.getBasicSalary()).grossSalary(gross)
                .allowances(structure.getAllowances()).bonuses(structure.getBonuses()).deductions(structure.getDeductions())
                .netSalary(net).status(PayrollStatus.DRAFT).lastModifiedBy(admin).build());
        audit(admin, "PAYROLL_GENERATED", "PAYROLL", record.getId(), year + "-" + month);
        return map(record);
    }

    @Transactional(readOnly = true) public List<PayrollRecordDTO> allPayroll() { return payrollRepository.findAll().stream().map(this::map).toList(); }
    @Transactional(readOnly = true) public List<PayrollRecordDTO> myPayroll(Long teacherId) { return payrollRepository.findByTeacherIdOrderByPayrollYearDescPayrollMonthDesc(teacherId).stream().map(this::map).toList(); }

    @Transactional(readOnly = true)
    public PayrollDashboardDTO dashboard() {
        BigDecimal gross = BigDecimal.ZERO, deductions = BigDecimal.ZERO, net = BigDecimal.ZERO, paid = BigDecimal.ZERO;
        List<PayrollDashboardDTO.TeacherPayrollSummary> teachers = new java.util.ArrayList<>();
        for (PayrollRecord record : payrollRepository.findAll()) {
            gross = gross.add(record.getGrossSalary()); deductions = deductions.add(record.getDeductions()); net = net.add(record.getNetSalary());
            if (record.getStatus() == PayrollStatus.PAID || record.getStatus() == PayrollStatus.CREDITED) paid = paid.add(record.getNetSalary());
            teachers.add(new PayrollDashboardDTO.TeacherPayrollSummary(record.getTeacher().getId(), record.getTeacher().getUser().getFullName(), record.getGrossSalary(), record.getDeductions(), record.getNetSalary(), record.getStatus()));
        }
        return new PayrollDashboardDTO(gross, deductions, net, paid, net.subtract(paid).max(BigDecimal.ZERO), teachers);
    }

    @Transactional
    public PayrollRecordDTO approve(Long id, Long adminId) {
        User admin = administrator(adminId);
        PayrollRecord record = payrollRepository.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Payroll record not found"));
        if (record.getStatus() != PayrollStatus.DRAFT && record.getStatus() != PayrollStatus.PENDING) throw new IllegalArgumentException("Only draft payroll can be approved");
        record.setStatus(PayrollStatus.APPROVED); record.setLastModifiedBy(admin); payrollRepository.save(record);
        audit(admin, "PAYROLL_APPROVED", "PAYROLL", id, "net=" + record.getNetSalary());
        return map(record);
    }

    @Transactional
    public PayrollRecordDTO recordExternalPayment(Long id, PayrollPaymentRequest request, Long adminId) {
        User admin = administrator(adminId);
        PayrollRecord record = payrollRepository.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Payroll record not found"));
        if (record.getStatus() != PayrollStatus.APPROVED && record.getStatus() != PayrollStatus.FAILED) throw new IllegalArgumentException("Payroll must be approved before payment");
        if (request == null || request.paymentMethod() == null || request.paymentMethod().isBlank()
                || request.paymentReference() == null || request.paymentReference().isBlank()
                || request.paymentDate() == null) throw new IllegalArgumentException("Payment method, reference and date are required");
        record.setStatus(PayrollStatus.PAID); record.setPaymentMethod(request.paymentMethod().trim()); record.setPaymentReference(request.paymentReference().trim());
        record.setPaymentDate(request.paymentDate()); record.setPaymentNotes(request.paymentNotes()); record.setLastModifiedBy(admin);
        payrollRepository.save(record); audit(admin, "PAYROLL_EXTERNAL_PAYMENT_RECORDED", "PAYROLL", id, "method=" + record.getPaymentMethod() + ", reference=" + record.getPaymentReference());
        return map(record);
    }

    @Transactional(readOnly = true)
    public byte[] payslip(Long id, User requester) {
        PayrollRecord record = payrollRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payroll record not found"));
        boolean admin = requester.getRole() == Role.ADMIN || requester.getRole() == Role.ADMINISTRATOR;
        boolean owner = record.getTeacher() != null && record.getTeacher().getUser() != null
                && requester.getId().equals(record.getTeacher().getUser().getId());
        if (!admin && !owner) throw new AccessDeniedException("You are not authorized to access this payslip");
        if (record.getStatus() != PayrollStatus.PAID && record.getStatus() != PayrollStatus.CREDITED)
            throw new IllegalArgumentException("Payslip is available only after payroll is paid");
        if (receiptPdfService == null) throw new IllegalStateException("Payslip renderer is unavailable");
        return receiptPdfService.renderPayslip(record);
    }

    @Transactional
    public PayrollRecordDTO updateStatus(Long id, PayrollStatus status, LocalDate paymentDate, String reference, String payslip, Long adminId) {
        if (status == PayrollStatus.APPROVED) return approve(id, adminId);
        if (status == PayrollStatus.PAID || status == PayrollStatus.CREDITED) {
            return recordExternalPayment(id, new PayrollPaymentRequest("EXTERNAL", reference, paymentDate, null), adminId);
        }
        User admin = administrator(adminId); PayrollRecord record = payrollRepository.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Payroll record not found"));
        if (status == null) throw new IllegalArgumentException("Payroll status is required");
        if (status == PayrollStatus.DRAFT || status == PayrollStatus.PENDING || status == PayrollStatus.PROCESSING) throw new IllegalArgumentException("Use the controlled payroll workflow for this status");
        record.setStatus(status); record.setPaymentDate(paymentDate); record.setPaymentReference(reference); record.setPayslipReference(payslip); record.setLastModifiedBy(admin); payrollRepository.save(record);
        audit(admin, "PAYROLL_STATUS_CHANGED", "PAYROLL", id, "status=" + status); return map(record);
    }

    private void validatePeriod(Integer year, Integer month) { if (year == null || month == null || year < 2000 || month < 1 || month > 12) throw new IllegalArgumentException("Valid payroll year and month are required"); }
    private void validateStructure(SalaryStructureDTO d) { if (d == null || d.getTeacherId() == null || (d.getBasicSalary() == null && d.getGrossSalary() == null) || zero(d.getBasicSalary() == null ? d.getGrossSalary() : d.getBasicSalary()).signum() < 0 || zero(d.getAllowances()).signum() < 0 || zero(d.getBonuses()).signum() < 0 || zero(d.getDeductions()).signum() < 0 || d.getEffectiveDate() == null || d.getPaymentFrequency() == null || d.getPaymentFrequency().isBlank()) throw new IllegalArgumentException("Teacher, non-negative salary values, frequency, and effective date are required"); }
    private BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP); }
    private User administrator(Long id) { User user = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found")); if (user.getRole() != Role.ADMIN && user.getRole() != Role.ADMINISTRATOR) throw new AccessDeniedException("Administrator permission required"); return user; }
    private void audit(User user, String action, String type, Object id, String details) { if (auditService != null) auditService.record(user, action, type, id, details); }
    private SalaryStructureDTO map(SalaryStructure s) { return SalaryStructureDTO.builder().id(s.getId()).teacherId(s.getTeacher().getId()).teacherName(s.getTeacher().getUser().getFullName()).basicSalary(s.getBasicSalary()).grossSalary(s.getGrossSalary()).allowances(s.getAllowances()).bonuses(s.getBonuses()).deductions(s.getDeductions()).paymentFrequency(s.getPaymentFrequency()).effectiveDate(s.getEffectiveDate()).build(); }
    private PayrollRecordDTO map(PayrollRecord p) { return PayrollRecordDTO.builder().id(p.getId()).teacherId(p.getTeacher().getId()).teacherName(p.getTeacher().getUser().getFullName()).payrollYear(p.getPayrollYear()).payrollMonth(p.getPayrollMonth()).basicSalary(p.getBasicSalary()).grossSalary(p.getGrossSalary()).allowances(p.getAllowances()).bonuses(p.getBonuses()).deductions(p.getDeductions()).netSalary(p.getNetSalary()).status(p.getStatus()).paymentDate(p.getPaymentDate()).paymentReference(p.getPaymentReference()).payslipReference(p.getPayslipReference()).paymentMethod(p.getPaymentMethod()).paymentNotes(p.getPaymentNotes()).build(); }
}
