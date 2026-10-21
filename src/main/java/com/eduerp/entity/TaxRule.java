package com.eduerp.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity @Table(name = "tax_rules", uniqueConstraints = @UniqueConstraint(name = "uk_tax_rules_code_from", columnNames = {"code", "valid_from"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TaxRule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 40) private String code;
    @Column(nullable = false, length = 120) private String name;
    @Column(nullable = false, length = 80) private String jurisdiction;
    @Column(name = "category_pattern", nullable = false, length = 120) private String categoryPattern;
    @Column(nullable = false, precision = 7, scale = 4) private BigDecimal rate;
    @Column(name = "price_includes_tax", nullable = false) @Builder.Default private Boolean priceIncludesTax = false;
    @Column(name = "applies_to_fees", nullable = false) @Builder.Default private Boolean appliesToFees = true;
    @Column(name = "applies_to_payroll", nullable = false) @Builder.Default private Boolean appliesToPayroll = false;
    @Column(name = "valid_from", nullable = false) private LocalDate validFrom;
    @Column(name = "valid_to") private LocalDate validTo;
    @Column(nullable = false) @Builder.Default private Boolean enabled = true;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by", nullable = false) private User createdBy;
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    @PrePersist void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
