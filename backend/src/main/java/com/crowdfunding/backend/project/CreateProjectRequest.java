package com.crowdfunding.backend.project;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Future;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateProjectRequest(
    @NotBlank(message = "El título es obligatorio")
    String title,

    @NotBlank(message = "La descripción es obligatoria")
    String description,

    @NotNull(message = "La meta de financiamiento es obligatoria")
    @DecimalMin(value = "0.01", message = "La meta debe ser mayor a cero")
    BigDecimal fundingGoal,

    String imageUrl,
    @NotNull(message = "La fecha límite es obligatoria")
    @Future(message = "La fecha límite debe ser en el futuro")
    LocalDateTime deadline
) {}
