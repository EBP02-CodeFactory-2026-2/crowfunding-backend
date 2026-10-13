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
    Integer scoring,     
    String riskLabel    
) {
    public static ProjectListResponse fromEntity(Project project) {
        Integer score = project.getScoring(); 
        String risk = calculateRiskLabel(score);

        return new ProjectListResponse(
            project.getId(),
            project.getTitle(),
            project.getDescription(),
            project.getFundingGoal(),
            project.getCurrentAmount(),
            project.getImageUrl(),
            project.getDeadline(),
            project.getStatus() != null ? project.getStatus().toString() : null,
            score,
            risk
        );
    }

    private static String calculateRiskLabel(Integer score) {
        if (score == null) return "SIN EVALUAR";
        if (score >= 80) return "BAJO RIESGO";
        if (score >= 50) return "RIESGO MEDIO";
        return "ALTO RIESGO";
    }
}