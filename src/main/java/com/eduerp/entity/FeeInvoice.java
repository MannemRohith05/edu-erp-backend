package com.eduerp.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "fee_invoices", uniqueConstraints = @UniqueConstraint(name = "uk_fee_invoice_student_structure", columnNames = {"student_id", "fee_structure_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FeeInvoice {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "student_id")
    private Student student;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "fee_structure_id")
    private FeeStructure feeStructure;
    @Column(nullable = false, length = 80)
    private String category;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;
    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default private BigDecimal subtotal = BigDecimal.ZERO;
    @Column(name = "tax_total", nullable = false, precision = 12, scale = 2)
    @Builder.Default private BigDecimal taxTotal = BigDecimal.ZERO;
    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default private BigDecimal adjustment = BigDecimal.ZERO;
    @Column(nullable = false)
    private LocalDate dueDate;
    @Column(length = 255)
    private String adjustmentReason;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by")
    private User createdBy;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @PrePersist
    void onCreate() { createdAt = LocalDateTime.now(); }
}
