package com.crowdfunding.backend.project;

import com.crowdfunding.backend.entity.Project;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ProjectResponse(
    UUID id,
    String title,
    String description,
    String imageUrl,
    BigDecimal fundingGoal,
    BigDecimal currentAmount,
    LocalDateTime deadline,
    String status,
    String riskTag,
    LocalDateTime createdAt
) {
    public static ProjectResponse fromEntity(Project project) {
        return new ProjectResponse(
            project.getId(),
            project.getTitle(),
            project.getDescription(),
            project.getImageUrl(),
            project.getFundingGoal(),
            project.getCurrentAmount() != null ? project.getCurrentAmount() : BigDecimal.ZERO,
            project.getDeadline(),
            project.getStatus() != null ? project.getStatus().toString() : "DRAFT",
            null, // riskTag (null para borradores)
            project.getCreatedAt()
        );
    }
}