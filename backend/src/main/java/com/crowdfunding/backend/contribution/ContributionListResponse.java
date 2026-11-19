package com.crowdfunding.backend.contribution;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.crowdfunding.backend.entity.Contribution;
import com.crowdfunding.backend.entity.Project;

public record ContributionListResponse(
    UUID id,
    String projectName,
    UUID projectId,
    BigDecimal amount,
    String status,
    LocalDateTime createdAt
) {
    public static ContributionListResponse fromEntity(Contribution contribution) {
        Project project = contribution.getProject();
        return new ContributionListResponse(
            contribution.getId(),
            project.getTitle(),
            project.getId(),
            contribution.getAmount(),
            contribution.getStatus().name(),
            contribution.getCreatedAt()
        );
    }
}