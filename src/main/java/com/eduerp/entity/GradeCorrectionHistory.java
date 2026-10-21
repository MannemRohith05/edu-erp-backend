package com.eduerp.entity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity @Table(name = "grade_correction_history") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GradeCorrectionHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "grade_id") private Grade grade;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "changed_by") private User changedBy;
    @Column(name = "old_marks", nullable = false, precision = 10, scale = 2) private BigDecimal oldMarks;
    @Column(name = "new_marks", nullable = false, precision = 10, scale = 2) private BigDecimal newMarks;
    @Column(nullable = false, length = 500) private String reason; @Column(nullable = false) private LocalDateTime changedAt;
    @PrePersist void onCreate() { if (changedAt == null) changedAt = LocalDateTime.now(); }
}
