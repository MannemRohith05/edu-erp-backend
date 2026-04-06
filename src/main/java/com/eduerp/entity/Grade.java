package com.eduerp.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "grades")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Grade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @NotBlank(message = "Assignment name is required")
    @Column(nullable = false)
    private String assignmentName;

    @PositiveOrZero(message = "Marks cannot be negative")
    @Column(nullable = false)
    private Double marks;

    @PositiveOrZero(message = "Total marks cannot be negative")
    @Column(nullable = false)
    private Double totalMarks;

    private String gradeType; // ASSIGNMENT, QUIZ, MIDTERM, FINAL, PROJECT

    private String letterGrade; // A, B, C, D, F

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "graded_by")
    private User gradedBy;

    private LocalDateTime gradedAt;

    private String feedback;

    public Double getPercentage() {
        if (totalMarks == null || totalMarks == 0)
            return 0.0;
        return (marks / totalMarks) * 100;
    }
}
