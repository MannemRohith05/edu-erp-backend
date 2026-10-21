package com.eduerp.repository;

import com.eduerp.entity.FeeInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface FeeInvoiceRepository extends JpaRepository<FeeInvoice, Long> {
    List<FeeInvoice> findByStudentId(Long studentId);
    boolean existsByStudentIdAndFeeStructureId(Long studentId, Long feeStructureId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from FeeInvoice i where i.id = :id")
    Optional<FeeInvoice> findByIdForUpdate(@Param("id") Long id);
}
