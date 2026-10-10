package com.crowdfunding.backend.contribution;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ContributionRequest(
    @NotNull
    @DecimalMin(value = "10000", message = "El monto mínimo para aportar es $10.000")
    BigDecimal amount,
    @NotBlank(message = "El método de pago es obligatorio")
    String paymentMethod,
    @NotBlank(message = "El token de seguridad es obligatorio")
    String nonce
) {}
