package com.crowdfunding.backend.exception;

import java.util.List;

public class RiskScoreCalculationException extends RuntimeException {

    public record Issue(
            String field,
            String detail) {
    }

    private final List<Issue> issuesList;

    public RiskScoreCalculationException(String message, List<Issue> issues) {
        super(message);
        this.issuesList = issues;
    }

    public List<Issue> getIssuesList() {
        return issuesList;
    }

}
