package com.eduerp.dto;

import com.eduerp.entity.CertificateStatus;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CertificateDTO {
    private Long id; private String verificationRef; private Long studentId; private String studentName;
    private Long courseId; private String courseName; private String certificateType; private String title;
    private LocalDate issueDate; private CertificateStatus status; private Long issuedBy; private String issuedByName;
    private LocalDateTime issuedAt; private LocalDateTime updatedAt; private String notes;
}
