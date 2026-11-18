package com.crowdfunding.backend.contribution;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.crowdfunding.backend.entity.Project;
import com.crowdfunding.backend.entity.Contribution;

/**
 * ContributionListResponse
 */
public record ContributionListResponse(
    UUID id,
    String projectName,
    UUID projectId,
    BigDecimal amount,
    String status,
    LocalDateTime createdAt
) {
    public static ContributionResponse fromEntity(Contribution contribution, Project project){
       
        return new ContributionListResponse(
            contribution.getId(), 
            project.getTitle(),
            project.getId(), 
            contribution.getAmount(), 
            contribution.getStatus().toString(), 
            contribution.getCreatedAt()
        );
    }
}