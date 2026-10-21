package com.eduerp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinanceDashboardDTO {
    private BigDecimal totalFeesAssigned;
    private int studentsAssessed;
    private BigDecimal totalAmountReceived;
    private BigDecimal pendingBalance;
    private BigDecimal overdueBalance;
    private List<StudentFinanceSummary> students;

    public record StudentFinanceSummary(
            Long studentId,
            String studentName,
            BigDecimal assessed,
            BigDecimal paid,
            BigDecimal pending,
            BigDecimal overdue) {
    }
}
