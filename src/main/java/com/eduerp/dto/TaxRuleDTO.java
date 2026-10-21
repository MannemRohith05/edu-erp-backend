package com.eduerp.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class TaxRuleDTO {
    private Long id; private String code; private String name; private String jurisdiction; private String categoryPattern;
    private BigDecimal rate; private Boolean priceIncludesTax; private Boolean appliesToFees; private Boolean appliesToPayroll;
    private LocalDate validFrom; private LocalDate validTo; private Boolean enabled;
}
