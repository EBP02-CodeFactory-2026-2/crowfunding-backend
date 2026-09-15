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
    String status
) {
    public static ProjectListResponse fromEntity(Project project) {
        return new ProjectListResponse(
            project.getId(),
            project.getTitle(),
            project.getDescription(),
            project.getFundingGoal(),
            project.getCurrentAmount(),
            project.getImageUrl(),
            project.getDeadline(),
            project.getStatus() != null ? project.getStatus().toString() : null
        );
    }
}