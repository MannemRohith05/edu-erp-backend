package com.eduerp.repository;

import com.eduerp.entity.TaxRule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface TaxRuleRepository extends JpaRepository<TaxRule, Long> {
    List<TaxRule> findByEnabledTrueAndAppliesToFeesTrueAndValidFromLessThanEqual(LocalDate date);
}
