package com.eduerp.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity @Table(name = "assessments", uniqueConstraints = @UniqueConstraint(name = "uk_assessment_course_title", columnNames = {"course_id", "title"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Assessment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "course_id") private Course course;
    @Column(nullable = false, length = 120) private String title;
    @Column(name = "assessment_type", length = 30) private String assessmentType;
    @Column(name = "academic_year", length = 20) private String academicYear;
    private Integer semester;
    private java.time.LocalDate deadline;
    @Column(name = "max_marks", nullable = false, precision = 8, scale = 2) private BigDecimal maxMarks;
    @Column(nullable = false, precision = 6, scale = 2) private BigDecimal weightage;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) @Builder.Default private AssessmentStatus status = AssessmentStatus.DRAFT;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by") private User createdBy;
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    private LocalDateTime submittedAt; private LocalDateTime approvedAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "approved_by") private User approvedBy;
    @PrePersist void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
