package com.eduerp.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "salary_structures")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SalaryStructure {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "teacher_id", nullable = false) private Teacher teacher;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal grossSalary;
    @Column(nullable = false, precision = 12, scale = 2) @Builder.Default private BigDecimal allowances = BigDecimal.ZERO;
    @Column(nullable = false, precision = 12, scale = 2) @Builder.Default private BigDecimal deductions = BigDecimal.ZERO;
    @Column(nullable = false, length = 20) private String paymentFrequency;
    @Column(nullable = false) private LocalDate effectiveDate;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "configured_by", nullable = false) private User configuredBy;
    @Column(nullable = false) private LocalDateTime createdAt;
    @PrePersist void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
