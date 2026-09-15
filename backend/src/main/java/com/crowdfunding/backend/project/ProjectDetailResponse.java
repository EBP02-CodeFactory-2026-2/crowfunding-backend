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
    LocalDateTime createdAt
) {
    public static ProjectDetailResponse fromEntity(Project project) {
        return new ProjectDetailResponse(
            project.getId(),
            project.getTitle(),
            project.getDescription(),
            project.getFundingGoal(),
            project.getCurrentAmount(),
            project.getImageUrl(),
            project.getDeadline(),
            project.getStatus() != null ? project.getStatus().toString() : null,
            project.getCreatedAt()
        );
    }
}