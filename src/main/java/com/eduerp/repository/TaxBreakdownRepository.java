package com.eduerp.repository;

import com.eduerp.entity.TaxBreakdown;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TaxBreakdownRepository extends JpaRepository<TaxBreakdown, Long> {
    List<TaxBreakdown> findByInvoiceId(Long invoiceId);
}
