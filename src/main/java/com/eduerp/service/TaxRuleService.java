package com.eduerp.service;

import com.eduerp.dto.TaxRuleDTO;
import com.eduerp.entity.*;
import com.eduerp.exception.ResourceNotFoundException;
import com.eduerp.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service @RequiredArgsConstructor
public class TaxRuleService {
    private final TaxRuleRepository ruleRepository;
    private final TaxBreakdownRepository breakdownRepository;
    private final UserRepository userRepository;

    @Transactional
    public TaxRuleDTO create(TaxRuleDTO dto, Long adminId) {
        validate(dto);
        if (dto.getValidTo() != null && dto.getValidTo().isBefore(dto.getValidFrom())) throw new IllegalArgumentException("Tax rule end date must not precede start date");
        User admin = userRepository.findById(adminId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        TaxRule rule = TaxRule.builder().code(dto.getCode().trim()).name(dto.getName().trim()).jurisdiction(dto.getJurisdiction().trim()).categoryPattern(dto.getCategoryPattern().trim()).rate(dto.getRate()).priceIncludesTax(Boolean.TRUE.equals(dto.getPriceIncludesTax())).appliesToFees(!Boolean.FALSE.equals(dto.getAppliesToFees())).appliesToPayroll(Boolean.TRUE.equals(dto.getAppliesToPayroll())).validFrom(dto.getValidFrom()).validTo(dto.getValidTo()).enabled(!Boolean.FALSE.equals(dto.getEnabled())).createdBy(admin).build();
        return map(ruleRepository.save(rule));
    }
    @Transactional(readOnly = true) public List<TaxRuleDTO> all() { return ruleRepository.findAll().stream().map(this::map).toList(); }
    public TaxCalculation calculate(String category, BigDecimal enteredAmount, LocalDate date) {
        List<TaxRule> rules = ruleRepository.findByEnabledTrueAndAppliesToFeesTrueAndValidFromLessThanEqual(date).stream().filter(rule -> (rule.getValidTo() == null || !rule.getValidTo().isBefore(date)) && category.matches(rule.getCategoryPattern())).toList();
        if (rules.size() > 1) throw new IllegalArgumentException("Multiple active tax rules match fee category " + category);
        if (rules.isEmpty()) return new TaxCalculation(enteredAmount, BigDecimal.ZERO, enteredAmount, List.of());
        TaxRule rule = rules.get(0); BigDecimal rate = rule.getRate().divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
        BigDecimal subtotal = Boolean.TRUE.equals(rule.getPriceIncludesTax()) ? enteredAmount.divide(BigDecimal.ONE.add(rate), 2, RoundingMode.HALF_UP) : enteredAmount;
        BigDecimal tax = subtotal.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = Boolean.TRUE.equals(rule.getPriceIncludesTax()) ? enteredAmount : subtotal.add(tax);
        return new TaxCalculation(subtotal, tax, total, List.of(new TaxSnapshot(rule, subtotal, tax)));
    }
    @Transactional
    public void saveSnapshots(FeeInvoice invoice, TaxCalculation calculation) {
        for (TaxSnapshot snapshot : calculation.snapshots()) {
            TaxRule rule = snapshot.rule();
            breakdownRepository.save(TaxBreakdown.builder().invoice(invoice).taxRule(rule).ruleCode(rule.getCode()).ruleName(rule.getName()).jurisdiction(rule.getJurisdiction()).rate(rule.getRate()).taxableAmount(snapshot.taxableAmount()).taxAmount(snapshot.taxAmount()).priceIncludesTax(rule.getPriceIncludesTax()).build());
        }
    }
    private void validate(TaxRuleDTO d) { if (d.getCode() == null || d.getName() == null || d.getJurisdiction() == null || d.getCategoryPattern() == null || d.getRate() == null || d.getRate().signum() < 0 || d.getRate().compareTo(BigDecimal.valueOf(100)) > 0 || d.getValidFrom() == null) throw new IllegalArgumentException("Tax rule fields and a rate between 0 and 100 are required"); }
    private TaxRuleDTO map(TaxRule r) { TaxRuleDTO d = new TaxRuleDTO(); d.setId(r.getId()); d.setCode(r.getCode()); d.setName(r.getName()); d.setJurisdiction(r.getJurisdiction()); d.setCategoryPattern(r.getCategoryPattern()); d.setRate(r.getRate()); d.setPriceIncludesTax(r.getPriceIncludesTax()); d.setAppliesToFees(r.getAppliesToFees()); d.setAppliesToPayroll(r.getAppliesToPayroll()); d.setValidFrom(r.getValidFrom()); d.setValidTo(r.getValidTo()); d.setEnabled(r.getEnabled()); return d; }
    public record TaxCalculation(BigDecimal subtotal, BigDecimal taxTotal, BigDecimal total, List<TaxSnapshot> snapshots) { }
    public record TaxSnapshot(TaxRule rule, BigDecimal taxableAmount, BigDecimal taxAmount) { }
}
