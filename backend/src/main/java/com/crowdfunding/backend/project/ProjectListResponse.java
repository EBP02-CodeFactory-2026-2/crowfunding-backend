package com.crowdfunding.backend.project;

import com.crowdfunding.backend.entity.Project;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ProjectListResponse(
    UUID id,
    String title,
    String description,
    BigDecimal fundingGoal,
    BigDecimal currentAmount,
    String imageUrl,
    LocalDateTime deadline,
    String status,
    String riskTag,
    Integer backersCount // Agregamos backersCount
) {
    public static ProjectListResponse fromEntity(Project project, Integer backersCount) {
        Integer score = project.getRiskScore();
        String tag = calculateRiskTag(score);

        return new ProjectListResponse(
            project.getId(),
            project.getTitle(),
            project.getDescription(),
            project.getFundingGoal(),
            project.getCurrentAmount(),
            project.getImageUrl(),
            project.getDeadline(),
            project.getStatus() != null ? project.getStatus().toString() : null,
            tag,
            backersCount != null ? backersCount : 0
        );
    }

    public static ProjectListResponse fromEntity(Project project) {
        return fromEntity(project, 0);
    }

    private static String calculateRiskTag(Integer score) {
        if (score == null) return "Sin evaluar";
        if (score <= 1) return "Riesgo Alto";
        if (score <= 5) return "Riesgo Medio";
        return "Riesgo Bajo";
    }
}