package com.crowdfunding.backend.interfaces;

import com.crowdfunding.backend.entity.Project;
import com.crowdfunding.backend.entity.User;

public interface ScoringEngine {

    public int calculateRiskScore(Project project, User creator);
}
