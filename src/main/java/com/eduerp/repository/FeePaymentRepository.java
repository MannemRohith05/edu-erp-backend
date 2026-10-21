package com.eduerp.repository;

import com.eduerp.entity.FeePayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface FeePaymentRepository extends JpaRepository<FeePayment, Long> {
    boolean existsByIdempotencyKey(String idempotencyKey);
    Optional<FeePayment> findByIdempotencyKey(String idempotencyKey);
    List<FeePayment> findByInvoiceIdOrderByPaidAtDesc(Long invoiceId);
    boolean existsByReference(String reference);
    Optional<FeePayment> findByProviderOrderId(String providerOrderId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from FeePayment p where p.providerOrderId = :providerOrderId")
    Optional<FeePayment> findByProviderOrderIdForUpdate(@Param("providerOrderId") String providerOrderId);
    Optional<FeePayment> findByProviderPaymentId(String providerPaymentId);
}
