package com.eduerp.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import com.eduerp.entity.FeePaymentStatus;

@Data
public class FeePaymentDTO {
    private Long invoiceId;
    private Long studentId;
    private String studentName;
    private String category;
    private BigDecimal amount;
    private BigDecimal adjustment;
    private BigDecimal subtotal;
    private BigDecimal taxTotal;
    private BigDecimal paid;
    private BigDecimal balance;
    private LocalDate dueDate;
    private String adjustmentReason;
    private List<PaymentRecord> payments;
    private FeePaymentStatus status;
    private String provider;
    private String providerOrderId;
    private String providerPaymentId;
    private String providerKeyId;
    private BigDecimal providerAmount;
    private String providerCurrency;

    public record PaymentRecord(BigDecimal amount, String reference, LocalDateTime paidAt, String notes, FeePaymentStatus status) {}
}
