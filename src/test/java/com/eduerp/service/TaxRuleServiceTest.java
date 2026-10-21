package com.eduerp.service;

import com.eduerp.repository.TaxBreakdownRepository;
import com.eduerp.repository.TaxRuleRepository;
import com.eduerp.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaxRuleServiceTest {
    @Mock private TaxRuleRepository ruleRepository;
    @Mock private TaxBreakdownRepository breakdownRepository;
    @Mock private UserRepository userRepository;

    @Test
    void noApplicableTaxRuleKeepsTheFeeAmountAsTheInvoiceTotal() {
        TaxRuleService service = new TaxRuleService(ruleRepository, breakdownRepository, userRepository);
        when(ruleRepository.findByEnabledTrueAndAppliesToFeesTrueAndValidFromLessThanEqual(anyDate())).thenReturn(List.of());

        TaxRuleService.TaxCalculation result = service.calculate("Bus", new BigDecimal("37000.00"), LocalDate.of(2026, 10, 20));

        assertEquals(new BigDecimal("37000.00"), result.subtotal());
        assertEquals(BigDecimal.ZERO, result.taxTotal());
        assertEquals(new BigDecimal("37000.00"), result.total());
    }

    private LocalDate anyDate() {
        return LocalDate.of(2026, 10, 20);
    }
}
