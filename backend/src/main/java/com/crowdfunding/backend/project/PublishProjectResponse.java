package com.crowdfunding.backend.project;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PublishProjectResponse(
        UUID id,
        String title,
        String status,
        int riskScore,
        String riskTag,
        BigDecimal fundingGoal,
        LocalDateTime deadLine,
        LocalDateTime updatedAt) {

}
