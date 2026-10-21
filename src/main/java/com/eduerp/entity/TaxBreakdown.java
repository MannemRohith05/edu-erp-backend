package com.eduerp.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity @Table(name = "tax_breakdowns", uniqueConstraints = @UniqueConstraint(name = "uk_tax_breakdown_invoice_rule", columnNames = {"invoice_id", "tax_rule_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TaxBreakdown {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "invoice_id") private FeeInvoice invoice;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "tax_rule_id") private TaxRule taxRule;
    @Column(name = "rule_code", nullable = false, length = 40) private String ruleCode;
    @Column(name = "rule_name", nullable = false, length = 120) private String ruleName;
    @Column(nullable = false, length = 80) private String jurisdiction;
    @Column(nullable = false, precision = 7, scale = 4) private BigDecimal rate;
    @Column(name = "taxable_amount", nullable = false, precision = 12, scale = 2) private BigDecimal taxableAmount;
    @Column(name = "tax_amount", nullable = false, precision = 12, scale = 2) private BigDecimal taxAmount;
    @Column(name = "price_includes_tax", nullable = false) private Boolean priceIncludesTax;
}
