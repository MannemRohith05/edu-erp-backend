package com.eduerp.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class FeeStructureDTO {
    private Long id;
    private String category;
    private BigDecimal amount;
    private LocalDate dueDate;
    private Integer semester;
    private String academicYear;
    private Long courseId;
    private Boolean allowPartial;
}
