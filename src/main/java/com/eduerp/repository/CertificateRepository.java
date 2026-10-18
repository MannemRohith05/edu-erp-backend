package com.eduerp.repository;

import com.eduerp.entity.Certificate;
import com.eduerp.entity.CertificateStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CertificateRepository extends JpaRepository<Certificate, Long> {
    List<Certificate> findByStudentIdOrderByIssueDateDesc(Long studentId);
    List<Certificate> findByStatusOrderByIssueDateDesc(CertificateStatus status);
}
