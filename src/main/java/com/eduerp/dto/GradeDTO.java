package com.eduerp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GradeDTO {
    private Long id;
    private Long assessmentId;
    private Long studentId;
    private String studentName;
    private String studentCode;
    private Long courseId;
    private String courseName;
    private String assignmentName;
    private Double marks;
    private Double totalMarks;
    private Double percentage;
    private String gradeType;
    private String letterGrade;
    private Boolean published;
    private String workflowStatus;
    private String feedback;
    private String correctionReason;
    private LocalDateTime gradedAt;
}
