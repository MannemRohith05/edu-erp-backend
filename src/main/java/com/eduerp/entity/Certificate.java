package com.eduerp.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "certificates")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Certificate {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "verification_ref", nullable = false, unique = true, length = 48) private String verificationRef;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "student_id", nullable = false) private Student student;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "course_id") private Course course;
    @Column(nullable = false, length = 100) private String certificateType;
    @Column(nullable = false, length = 200) private String title;
    @Column(nullable = false) private LocalDate issueDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) @Builder.Default private CertificateStatus status = CertificateStatus.ISSUED;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "issued_by", nullable = false) private User issuedBy;
    @Column(nullable = false) private LocalDateTime issuedAt;
    private LocalDateTime updatedAt;
    @Column(length = 500) private String notes;
    @PrePersist void onCreate() { if (issuedAt == null) issuedAt = LocalDateTime.now(); if (updatedAt == null) updatedAt = issuedAt; }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }
}
