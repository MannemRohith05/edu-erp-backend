package com.eduerp.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SalaryStructureDTO {
    private Long id; private Long teacherId; private String teacherName; private BigDecimal basicSalary; private BigDecimal grossSalary;
    private BigDecimal allowances; private BigDecimal bonuses; private BigDecimal deductions; private String paymentFrequency; private LocalDate effectiveDate;
}
