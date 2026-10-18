package com.eduerp.dto;

import com.eduerp.entity.PayrollStatus;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PayrollRecordDTO {
    private Long id; private Long teacherId; private String teacherName; private Integer payrollYear; private Integer payrollMonth;
    private BigDecimal grossSalary; private BigDecimal allowances; private BigDecimal deductions; private BigDecimal netSalary;
    private PayrollStatus status; private LocalDate paymentDate; private String paymentReference; private String payslipReference;
}
