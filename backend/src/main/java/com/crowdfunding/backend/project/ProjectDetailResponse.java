package com.crowdfunding.backend.project;

import com.crowdfunding.backend.entity.Project;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ProjectDetailResponse(
    UUID id,
    String title,
    String description,
    BigDecimal fundingGoal,
    BigDecimal currentAmount,
    String imageUrl,
    LocalDateTime deadline,
    String status,
    CreatorDto creator,
    LocalDateTime createdAt,
    String riskTag 
) {

    // Record anidado para el objeto creador
    public record CreatorDto(Long id, String fullName) {}

    public static ProjectDetailResponse fromEntity(Project project) {
        Integer score = project.getRiskScore();
        String tag = calculateRiskTag(score);

        return new ProjectDetailResponse(
            project.getId(),
            project.getTitle(),
            project.getDescription(),
            project.getFundingGoal(),
            project.getCurrentAmount(),
            project.getImageUrl(),
            project.getDeadline(),
            project.getStatus(),
            project.getCreator() != null ? 
                new CreatorDto(project.getCreator().getId(), project.getCreator().getFullName()) : null,
            project.getCreatedAt(),
            tag 
        );
    }

    private static String calculateRiskTag(Integer score) {
        if (score == null) return "Sin evaluar";
        if (score <= 1) return "Riesgo Alto";
        if (score <= 5) return "Riesgo Medio";
        return "Riesgo Bajo";
    }
}