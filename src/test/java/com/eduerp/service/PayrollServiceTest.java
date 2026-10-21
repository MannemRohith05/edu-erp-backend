package com.eduerp.service;

import com.eduerp.dto.PayrollPaymentRequest;
import com.eduerp.dto.SalaryStructureDTO;
import com.eduerp.entity.*;
import com.eduerp.repository.PayrollRecordRepository;
import com.eduerp.repository.SalaryStructureRepository;
import com.eduerp.repository.TeacherRepository;
import com.eduerp.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayrollServiceTest {
    @Mock private SalaryStructureRepository structures;
    @Mock private PayrollRecordRepository payroll;
    @Mock private TeacherRepository teachers;
    @Mock private UserRepository users;
    @Mock private ReceiptPdfService pdf;
    @InjectMocks private PayrollService service;

    private final User admin = User.builder().id(1L).role(Role.ADMIN).firstName("Admin").lastName("User").email("a@test.invalid").password("secret").build();
    private final User teacherUser = User.builder().id(2L).role(Role.TEACHER).firstName("Tina").lastName("Teacher").email("t@test.invalid").password("secret").build();
    private final Teacher teacher = Teacher.builder().id(3L).user(teacherUser).employeeId("T-3").build();

    @Test
    void generatesGrossAndNetFromBasicAllowancesBonusesAndDeductions() {
        when(users.findById(1L)).thenReturn(Optional.of(admin));
        when(teachers.findById(3L)).thenReturn(Optional.of(teacher));
        when(payroll.findByTeacherIdAndPayrollYearAndPayrollMonth(3L, 2026, 1)).thenReturn(Optional.empty());
        SalaryStructure structure = SalaryStructure.builder().id(4L).teacher(teacher).basicSalary(new BigDecimal("50000"))
                .grossSalary(new BigDecimal("55000")).allowances(new BigDecimal("3000")).bonuses(new BigDecimal("2000"))
                .deductions(new BigDecimal("5000")).effectiveDate(LocalDate.of(2025, 1, 1)).paymentFrequency("MONTHLY").build();
        when(structures.findByTeacherIdOrderByEffectiveDateDesc(3L)).thenReturn(java.util.List.of(structure));
        when(payroll.save(any(PayrollRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.generate(3L, 2026, 1, 1L);

        assertEquals(new BigDecimal("55000.00"), result.getGrossSalary());
        assertEquals(new BigDecimal("50000.00"), result.getNetSalary());
        assertEquals(PayrollStatus.DRAFT, result.getStatus());
    }

    @Test
    void requiresApprovalBeforeRecordingExternalPayment() {
        when(users.findById(1L)).thenReturn(Optional.of(admin));
        PayrollRecord record = PayrollRecord.builder().id(8L).teacher(teacher).status(PayrollStatus.DRAFT)
                .basicSalary(BigDecimal.TEN).grossSalary(BigDecimal.TEN).allowances(BigDecimal.ZERO).bonuses(BigDecimal.ZERO)
                .deductions(BigDecimal.ZERO).netSalary(BigDecimal.TEN).payrollYear(2026).payrollMonth(1).build();
        when(payroll.findByIdForUpdate(8L)).thenReturn(Optional.of(record));

        assertThrows(IllegalArgumentException.class, () -> service.recordExternalPayment(8L,
                new PayrollPaymentRequest("BANK", "REF-8", LocalDate.now(), null), 1L));
    }

    @Test
    void teacherCannotDownloadAnotherTeachersPayslip() {
        PayrollRecord record = PayrollRecord.builder().id(8L).teacher(teacher).status(PayrollStatus.PAID)
                .basicSalary(BigDecimal.TEN).grossSalary(BigDecimal.TEN).allowances(BigDecimal.ZERO).bonuses(BigDecimal.ZERO)
                .deductions(BigDecimal.ZERO).netSalary(BigDecimal.TEN).payrollYear(2026).payrollMonth(1)
                .paymentReference("REF-8").paymentDate(LocalDate.now()).build();
        when(payroll.findById(8L)).thenReturn(Optional.of(record));
        User anotherTeacher = User.builder().id(99L).role(Role.TEACHER).firstName("Other").lastName("Teacher").build();

        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.payslip(8L, anotherTeacher));
    }
}
