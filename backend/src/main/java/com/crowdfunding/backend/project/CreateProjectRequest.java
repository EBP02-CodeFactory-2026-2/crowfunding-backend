package com.crowdfunding.backend.project;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateProjectRequest(
    String title,
    String description,
    BigDecimal fundingGoal,
    String imageUrl,
    LocalDateTime deadline
) {}