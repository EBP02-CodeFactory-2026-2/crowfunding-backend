package com.crowdfunding.backend.project;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;

public record CreateProjectRequest(
    @NotBlank(message = "El título es obligatorio")
    String title,
    @NotBlank(message = "La descripción es obligatoria")
    String description,
    BigDecimal fundingGoal,
    String imageUrl,
    LocalDateTime deadline
) {}