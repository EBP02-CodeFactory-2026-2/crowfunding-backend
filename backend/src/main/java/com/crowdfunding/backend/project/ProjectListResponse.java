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
        return new ProjectListResponse(
            project.getId(),
            project.getTitle(),
            project.getDescription(),
            project.getFundingGoal(),
            project.getCurrentAmount(),
            project.getImageUrl(),
            project.getDeadline(),
            project.getStatus().name(),
            project.getRiskTag(),
            backersCount != null ? backersCount : 0
        );
    }

    public static ProjectListResponse fromEntity(Project project) {
        return fromEntity(project, 0);
    }

}