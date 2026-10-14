package com.crowdfunding.backend.project;

import com.crowdfunding.backend.entity.Project;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record EditProjectResponse(
        UUID id,
        String title,
        String description,
        String imageUrl,
        BigDecimal fundingGoal,
        BigDecimal currentAmount,
        LocalDateTime deadline,
        String status,
        String riskTag,
        LocalDateTime updatedAt) {
    public static EditProjectResponse fromEntity(Project project) {
        Integer score = project.getRiskScore();
        String tag = calculateRiskTag(score);
        return new EditProjectResponse(
                project.getId(),
                project.getTitle(),
                project.getDescription(),
                project.getImageUrl(),
                project.getFundingGoal(),
                project.getCurrentAmount() != null ? project.getCurrentAmount() : BigDecimal.ZERO,
                project.getDeadline(),
                project.getStatus() != null ? project.getStatus().toString() : "DRAFT",
                tag,
                project.getUpdatedAt()
        );
    }

    private static String calculateRiskTag(Integer score) {
        if (score == null)
            return "Sin evaluar";
        if (score <= 1)
            return "Riesgo Alto";
        if (score <= 5)
            return "Riesgo Medio";
        return "Riesgo Bajo";
    }
}