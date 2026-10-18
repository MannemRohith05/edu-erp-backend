package com.eduerp.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "fee_payments", uniqueConstraints = @UniqueConstraint(name = "uk_fee_payment_reference", columnNames = "reference"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FeePayment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "invoice_id")
    private FeeInvoice invoice;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;
    @Column(nullable = false, length = 100)
    private String reference;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "recorded_by")
    private User recordedBy;
    @Column(nullable = false, updatable = false)
    private LocalDateTime paidAt;
    private String notes;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    @Builder.Default private FeePaymentStatus status = FeePaymentStatus.PENDING;
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 120)
    private String idempotencyKey;
    private LocalDateTime confirmedAt;
    @PrePersist
    void onCreate() { paidAt = LocalDateTime.now(); }
}
