package com.eduerp.service;

import com.eduerp.dto.CertificateDTO;
import com.eduerp.entity.*;
import com.eduerp.exception.ResourceNotFoundException;
import com.eduerp.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class CertificateService {
    private final CertificateRepository certificateRepository; private final StudentRepository studentRepository;
    private final CourseRepository courseRepository; private final UserRepository userRepository;

    @Transactional
    public CertificateDTO issue(CertificateDTO dto, Long adminId) {
        User admin = administrator(adminId);
        if (dto.getStudentId() == null || dto.getCertificateType() == null || dto.getCertificateType().isBlank() || dto.getTitle() == null || dto.getTitle().isBlank() || dto.getIssueDate() == null) throw new IllegalArgumentException("Student, type, title, and issue date are required");
        Student student = studentRepository.findById(dto.getStudentId()).orElseThrow(() -> new ResourceNotFoundException("Student not found"));
        Course course = dto.getCourseId() == null ? null : courseRepository.findById(dto.getCourseId()).orElseThrow(() -> new ResourceNotFoundException("Course not found"));
        Certificate value = Certificate.builder().verificationRef("CERT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24).toUpperCase()).student(student).course(course).certificateType(dto.getCertificateType().trim()).title(dto.getTitle().trim()).issueDate(dto.getIssueDate()).status(CertificateStatus.ISSUED).issuedBy(admin).notes(dto.getNotes()).build();
        return map(certificateRepository.save(value));
    }

    @Transactional(readOnly = true) public List<CertificateDTO> all() { return certificateRepository.findAll().stream().map(this::map).toList(); }
    @Transactional(readOnly = true) public List<CertificateDTO> mine(Long studentId) { return certificateRepository.findByStudentIdOrderByIssueDateDesc(studentId).stream().filter(c -> c.getStatus() == CertificateStatus.ISSUED).map(this::map).toList(); }
    @Transactional @SuppressWarnings("null") public CertificateDTO revoke(Long id, Long adminId) { administrator(adminId); Certificate c = certificateRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Certificate not found")); c.setStatus(CertificateStatus.REVOKED); return map(certificateRepository.save(c)); }
    @Transactional @SuppressWarnings("null") public CertificateDTO correct(Long id, CertificateDTO dto, Long adminId) { administrator(adminId); Certificate c = certificateRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Certificate not found")); if (dto.getTitle() != null && !dto.getTitle().isBlank()) c.setTitle(dto.getTitle().trim()); if (dto.getCertificateType() != null && !dto.getCertificateType().isBlank()) c.setCertificateType(dto.getCertificateType().trim()); if (dto.getIssueDate() != null) c.setIssueDate(dto.getIssueDate()); c.setNotes(dto.getNotes()); return map(certificateRepository.save(c)); }
    private User administrator(Long id) { User u = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found")); if (u.getRole() != Role.ADMIN && u.getRole() != Role.ADMINISTRATOR) throw new AccessDeniedException("Administrator permission required"); return u; }
    private CertificateDTO map(Certificate c) { return CertificateDTO.builder().id(c.getId()).verificationRef(c.getVerificationRef()).studentId(c.getStudent().getId()).studentName(c.getStudent().getUser().getFullName()).courseId(c.getCourse() == null ? null : c.getCourse().getId()).courseName(c.getCourse() == null ? null : c.getCourse().getCourseName()).certificateType(c.getCertificateType()).title(c.getTitle()).issueDate(c.getIssueDate()).status(c.getStatus()).issuedBy(c.getIssuedBy().getId()).issuedByName(c.getIssuedBy().getFullName()).issuedAt(c.getIssuedAt()).updatedAt(c.getUpdatedAt()).notes(c.getNotes()).build(); }
}
