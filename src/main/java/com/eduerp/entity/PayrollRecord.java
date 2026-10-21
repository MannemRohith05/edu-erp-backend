package com.eduerp.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "payroll_records", uniqueConstraints = @UniqueConstraint(name = "uk_payroll_teacher_period", columnNames = {"teacher_id", "payroll_year", "payroll_month"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PayrollRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "teacher_id", nullable = false) private Teacher teacher;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "salary_structure_id", nullable = false) private SalaryStructure salaryStructure;
    @Column(name = "payroll_year", nullable = false) private Integer payrollYear;
    @Column(name = "payroll_month", nullable = false) private Integer payrollMonth;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal grossSalary;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal basicSalary;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal allowances;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal bonuses;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal deductions;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal netSalary;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) @Builder.Default private PayrollStatus status = PayrollStatus.PENDING;
    private LocalDate paymentDate;
    @Column(length = 100) private String paymentReference;
    @Column(length = 255) private String payslipReference;
    @Column(length = 30) private String paymentMethod;
    @Column(length = 500) private String paymentNotes;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "last_modified_by") private User lastModifiedBy;
    @Column(nullable = false) private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); if (updatedAt == null) updatedAt = createdAt; }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }
}
