package com.eduerp.repository;

import com.eduerp.entity.FeePayment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FeePaymentRepository extends JpaRepository<FeePayment, Long> {
    boolean existsByIdempotencyKey(String idempotencyKey);
    List<FeePayment> findByInvoiceIdOrderByPaidAtDesc(Long invoiceId);
    boolean existsByReference(String reference);
}
