package com.eduerp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record PayrollPaymentRequest(@NotBlank String paymentMethod, @NotBlank String paymentReference,
                                    @NotNull LocalDate paymentDate, String paymentNotes) {}
