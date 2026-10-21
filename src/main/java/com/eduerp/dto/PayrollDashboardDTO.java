package com.eduerp.dto;

import com.eduerp.entity.PayrollStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayrollDashboardDTO {
    private BigDecimal totalGrossSalary;
    private BigDecimal totalDeductions;
    private BigDecimal totalNetSalary;
    private BigDecimal salaryPaid;
    private BigDecimal salaryPayable;
    private List<TeacherPayrollSummary> teachers;

    public record TeacherPayrollSummary(Long teacherId, String teacherName, BigDecimal grossSalary,
                                        BigDecimal deductions, BigDecimal netSalary, PayrollStatus status) { }
}
