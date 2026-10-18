package com.eduerp.repository;

import com.eduerp.entity.FeeInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FeeInvoiceRepository extends JpaRepository<FeeInvoice, Long> {
    List<FeeInvoice> findByStudentId(Long studentId);
    boolean existsByStudentIdAndFeeStructureId(Long studentId, Long feeStructureId);
}
