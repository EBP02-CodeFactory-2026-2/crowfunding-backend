package com.crowdfunding.backend.services;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.crowdfunding.backend.entity.Project;
import com.crowdfunding.backend.entity.User;
import com.crowdfunding.backend.exception.RiskScoreCalculationException;
import com.crowdfunding.backend.interfaces.ScoringEngine;
import com.crowdfunding.backend.persistence.ProjectRepository;

@Service
public class RiskScoringService implements ScoringEngine {
    private final ProjectRepository repository;
    private static final String CALCULATION_ERROR_MESSAGE = "No se pudo calcular el riesgo del proyecto, revisa la información e inténtalo de nuevo";

    // Estados del historial
    private static final String STATUS_FUNDED = "FUNDED";
    private static final String STATUS_FAILED = "FAILED";

    // Antigüedad
    private static final int MIN_ACCOUNT_AGE_DAYS = 7;
    private static final int ACCOUNT_AGE_POINTS = 2;

    // Historial
    private static final int FUNDED_HISTORY_POINTS = 2;
    private static final int FAILED_HISTORY_POINTS = -1;

    // Descripción
    private static final int LONG_DESCRIPTION_LENGTH = 150;
    private static final int MEDIUM_DESCRIPTION_LENGTH = 50;

    // Meta
    private static final BigDecimal GOAL_MIN = new BigDecimal("500000");
    private static final BigDecimal GOAL_MAX = new BigDecimal("50000000");

    // Plazo (en días)
    private static final int IDEAL_DEADLINE_MIN = 15;
    private static final int IDEAL_DEADLINE_MAX = 90;
    private static final int ACCEPTABLE_DEADLINE_MIN = 7;
    private static final int ACCEPTABLE_DEADLINE_MAX = 120;

    // Límites del puntaje total (CA5)
    private static final int SCORE_MIN = -5;
    private static final int SCORE_MAX = 9;

    public RiskScoringService(ProjectRepository repository) {
        this.repository = repository;
    }

    @Override
    public int calculateRiskScore(Project project, User creator){
        LocalDateTime instantTime = LocalDateTime.now();

        checkConsistency(project, creator, instantTime);

        int total = accountAgePoints(creator, instantTime)
                + historyPoints(creator)
                + descriptionPoints(project.getDescription())
                + imagePoints(project.getImageUrl())
                + goalPoints(project.getFundingGoal())
                + deadlinePoints(project.getDeadline(), instantTime);

        return Math.max(SCORE_MIN, Math.min(SCORE_MAX, total));
    }

    private void checkConsistency(Project project, User creator, LocalDateTime instantTime) {
        List<RiskScoreCalculationException.Issue> errors = new ArrayList<>();

        if (project.getFundingGoal().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add(new RiskScoreCalculationException.Issue("fundingGoal",
                    "La meta de financiamiento debe ser mayor que cero."));

        }

        if (!project.getDeadline().isAfter(instantTime)) {
            errors.add(new RiskScoreCalculationException.Issue("deadline",
                    "La fecha de fin del proyecto debe ser posterior a la fecha actual."));
        }

        if (creator.getCreatedAt() == null) {
            errors.add(new RiskScoreCalculationException.Issue("creator.createdAt",
                    "La fecha de creación del usuario no puede ser nula."));
        } else if (creator.getCreatedAt().isAfter(instantTime)) {
            errors.add(new RiskScoreCalculationException.Issue("creator.createdAt",
                    "La fecha de creación del usuario no puede ser posterior a la fecha actual."));

        }

        if (!errors.isEmpty()) {
            throw new RiskScoreCalculationException(
                    CALCULATION_ERROR_MESSAGE, errors);
        }

    }

    private int accountAgePoints(User creator, LocalDateTime instantTime) {
        long days = Duration.between(creator.getCreatedAt(), instantTime).toDays();
        if (days >= MIN_ACCOUNT_AGE_DAYS) {
            return ACCOUNT_AGE_POINTS;
        }
        return 0;
    }

    private int historyPoints(User creator) {
        int points = 0;
        if (repository.existsByCreatorIdAndStatus(creator.getId(), STATUS_FUNDED)) {
            points += FUNDED_HISTORY_POINTS;
        }
        if (repository.existsByCreatorIdAndStatus(creator.getId(), STATUS_FAILED)) {
            points += FAILED_HISTORY_POINTS;
        }
        return points;
    }

    private int descriptionPoints(String description) {
        int length = description.trim().length();
        if (length >= LONG_DESCRIPTION_LENGTH) {
            return 2;
        }
        if (length >= MEDIUM_DESCRIPTION_LENGTH) {
            return 0;
        }
        return -1;
    }

    private int imagePoints(String imageURL) {
        if (imageURL == null || imageURL.isBlank()) {
            return -1;
        } else {
            return 1;
        }
    }

    private int goalPoints(BigDecimal fundingGoal) {
        if (fundingGoal.compareTo(GOAL_MIN) >= 0 && fundingGoal.compareTo(GOAL_MAX) <= 0) {
            return 1;

        }
        return -2;
    }

    private int deadlinePoints(LocalDateTime deadline, LocalDateTime instantTime) {
        long days = Duration.between(instantTime, deadline).toDays();

        if (days >= IDEAL_DEADLINE_MIN && days <= IDEAL_DEADLINE_MAX) {
            return 1;
        }
        if (days >= ACCEPTABLE_DEADLINE_MIN && days <= ACCEPTABLE_DEADLINE_MAX) {
            return 0; // 7-14 o 91-120, porque el rango ideal ya se descartó arriba
        }
        return -1;

    }
}
