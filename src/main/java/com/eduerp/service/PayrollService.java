package com.eduerp.service;

import com.eduerp.dto.*;
import com.eduerp.entity.*;
import com.eduerp.exception.ResourceNotFoundException;
import com.eduerp.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service @RequiredArgsConstructor
public class PayrollService {
    private final SalaryStructureRepository structureRepository; private final PayrollRecordRepository payrollRepository; private final TeacherRepository teacherRepository; private final UserRepository userRepository;
    @Transactional public SalaryStructureDTO configure(SalaryStructureDTO dto, Long adminId) { User admin = administrator(adminId); validateStructure(dto); Teacher teacher = teacherRepository.findById(dto.getTeacherId()).orElseThrow(() -> new ResourceNotFoundException("Teacher not found")); SalaryStructure s = SalaryStructure.builder().teacher(teacher).grossSalary(dto.getGrossSalary()).allowances(zero(dto.getAllowances())).deductions(zero(dto.getDeductions())).paymentFrequency(dto.getPaymentFrequency().trim()).effectiveDate(dto.getEffectiveDate()).configuredBy(admin).build(); return map(structureRepository.save(s)); }
    @Transactional(readOnly = true) public List<SalaryStructureDTO> allStructures() { return structureRepository.findAll().stream().map(this::map).toList(); }
    @Transactional(readOnly = true) public List<SalaryStructureDTO> myStructures(Long teacherId) { return structureRepository.findByTeacherIdOrderByEffectiveDateDesc(teacherId).stream().map(this::map).toList(); }
    @Transactional public PayrollRecordDTO generate(Long teacherId, Integer year, Integer month, Long adminId) { User admin = administrator(adminId); if (year == null || month == null || month < 1 || month > 12) throw new IllegalArgumentException("Valid payroll year and month are required"); if (payrollRepository.findByTeacherIdAndPayrollYearAndPayrollMonth(teacherId, year, month).isPresent()) throw new IllegalArgumentException("Payroll already exists for this teacher and period"); Teacher teacher = teacherRepository.findById(teacherId).orElseThrow(() -> new ResourceNotFoundException("Teacher not found")); SalaryStructure s = structureRepository.findByTeacherIdOrderByEffectiveDateDesc(teacherId).stream().filter(x -> !x.getEffectiveDate().isAfter(LocalDate.of(year, month, 1).withDayOfMonth(1))).findFirst().orElseThrow(() -> new IllegalArgumentException("No effective salary structure exists for this period")); PayrollRecord p = PayrollRecord.builder().teacher(teacher).salaryStructure(s).payrollYear(year).payrollMonth(month).grossSalary(s.getGrossSalary()).allowances(s.getAllowances()).deductions(s.getDeductions()).netSalary(s.getGrossSalary().add(s.getAllowances()).subtract(s.getDeductions())).status(PayrollStatus.PENDING).lastModifiedBy(admin).build(); return map(payrollRepository.save(p)); }
    @Transactional(readOnly = true) public List<PayrollRecordDTO> allPayroll() { return payrollRepository.findAll().stream().map(this::map).toList(); }
    @Transactional(readOnly = true) public List<PayrollRecordDTO> myPayroll(Long teacherId) { return payrollRepository.findByTeacherIdOrderByPayrollYearDescPayrollMonthDesc(teacherId).stream().map(this::map).toList(); }
    @Transactional public PayrollRecordDTO updateStatus(Long id, PayrollStatus status, LocalDate paymentDate, String reference, String payslip, Long adminId) { User admin = administrator(adminId); PayrollRecord p = payrollRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Payroll record not found")); if (status == null) throw new IllegalArgumentException("Payroll status is required"); p.setStatus(status); p.setPaymentDate(paymentDate); p.setPaymentReference(reference); p.setPayslipReference(payslip); p.setLastModifiedBy(admin); return map(payrollRepository.save(p)); }
    private void validateStructure(SalaryStructureDTO d) { if (d.getTeacherId() == null || d.getGrossSalary() == null || d.getGrossSalary().signum() < 0 || zero(d.getAllowances()).signum() < 0 || zero(d.getDeductions()).signum() < 0 || d.getEffectiveDate() == null || d.getPaymentFrequency() == null || d.getPaymentFrequency().isBlank()) throw new IllegalArgumentException("Teacher, non-negative salary values, frequency, and effective date are required"); }
    private BigDecimal zero(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
    private User administrator(Long id) { User u = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found")); if (u.getRole() != Role.ADMIN && u.getRole() != Role.ADMINISTRATOR) throw new AccessDeniedException("Administrator permission required"); return u; }
    private SalaryStructureDTO map(SalaryStructure s) { return SalaryStructureDTO.builder().id(s.getId()).teacherId(s.getTeacher().getId()).teacherName(s.getTeacher().getUser().getFullName()).grossSalary(s.getGrossSalary()).allowances(s.getAllowances()).deductions(s.getDeductions()).paymentFrequency(s.getPaymentFrequency()).effectiveDate(s.getEffectiveDate()).build(); }
    private PayrollRecordDTO map(PayrollRecord p) { return PayrollRecordDTO.builder().id(p.getId()).teacherId(p.getTeacher().getId()).teacherName(p.getTeacher().getUser().getFullName()).payrollYear(p.getPayrollYear()).payrollMonth(p.getPayrollMonth()).grossSalary(p.getGrossSalary()).allowances(p.getAllowances()).deductions(p.getDeductions()).netSalary(p.getNetSalary()).status(p.getStatus()).paymentDate(p.getPaymentDate()).paymentReference(p.getPaymentReference()).payslipReference(p.getPayslipReference()).build(); }
}
