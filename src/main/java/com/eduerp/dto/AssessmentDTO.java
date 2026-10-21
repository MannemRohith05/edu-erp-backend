package com.eduerp.dto;
import com.eduerp.entity.AssessmentStatus;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Data public class AssessmentDTO { private Long id; private Long courseId; private String courseName; private String title; private String assessmentType; private String academicYear; private Integer semester; private java.time.LocalDate deadline; private BigDecimal maxMarks; private BigDecimal weightage; private AssessmentStatus status; private LocalDateTime submittedAt; private LocalDateTime approvedAt; }
