package com.crowdfunding.backend.project;
import java.math.BigDecimal;
import java.time.LocalDate;
public class CreateProjectRequest {
private String title;
    private String description;
    private BigDecimal goalAmount;
    private LocalDate deadline;
    private Long creatorId;
    // Constructores
    public CreateProjectRequest() {
    }
    
    public CreateProjectRequest(String title, String description, BigDecimal goalAmount, LocalDate deadline, Long creatorId) {
        this.title = title;
        this.description = description;
        this.goalAmount = goalAmount;
        this.deadline = deadline;
        this.creatorId = creatorId;
    }
    // Getters y Setters
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getGoalAmount() {
        return goalAmount;
    }

    public void setGoalAmount(BigDecimal goalAmount) {
        this.goalAmount = goalAmount;
    }

    public LocalDate getDeadline() {
        return deadline;
    }

    public void setDeadline(LocalDate deadline) {
        this.deadline = deadline;
    }

    public Long getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(Long creatorId) {
        this.creatorId = creatorId;
    }
}