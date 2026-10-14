package com.crowdfunding.backend.services;

import com.crowdfunding.backend.entity.Project;
import com.crowdfunding.backend.entity.User;
import com.crowdfunding.backend.exception.MissingProjectFieldsException;
import com.crowdfunding.backend.exception.ProjectAccessDeniedException;
import com.crowdfunding.backend.exception.ProjectLockedException;
import com.crowdfunding.backend.exception.ResourceNotFoundException;
import com.crowdfunding.backend.interfaces.ScoringEngine;
import com.crowdfunding.backend.persistence.ProjectRepository;
import com.crowdfunding.backend.persistence.UserRepository;
import com.crowdfunding.backend.project.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ScoringEngine scoringEngine;
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_ACTIVE = "ACTIVE";

    public ProjectService(ProjectRepository projectRepository, UserRepository userRepository,
            ScoringEngine scoringEngine) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.scoringEngine = scoringEngine;
    }

    @Transactional(readOnly = true)
    public List<ProjectListResponse> getAllProjects() {
        return projectRepository.findAll()
                .stream()
                .map(ProjectListResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectDetailResponse getProjectById(UUID id) {
        return projectRepository.findById(id)
                .map(ProjectDetailResponse::fromEntity)
                .orElseThrow(() -> new ResourceNotFoundException("Proyecto no encontrado con el ID: " + id));
    }

    @Transactional
    public ProjectResponse createProject(CreateProjectRequest request) {
        String userEmail = SecurityContextHolder.getContext().getAuthentication().getName();

        User creator = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario autenticado no encontrado: " + userEmail));

        Project project = new Project();
        project.setTitle(request.title());
        project.setDescription(request.description());
        project.setFundingGoal(request.fundingGoal());
        project.setCurrentAmount(BigDecimal.ZERO);
        project.setImageUrl(request.imageUrl());
        project.setDeadline(request.deadline());
        project.setCreator(creator);

        Project savedProject = projectRepository.save(project);
        return ProjectResponse.fromEntity(savedProject);
    }

    @Transactional
    public PublishProjectResponse publishProject(UUID projectId) {
        String userEmail = SecurityContextHolder.getContext().getAuthentication().getName();

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Usuario autenticado no encontrado: " + userEmail));

        return publishProject(projectId, user.getId());
    }

    @Transactional
    public PublishProjectResponse publishProject(UUID projectId, Long userId) {

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Proyecto no encontrado con el ID: " + projectId));

        // Error 403: el usuario autenticado no es el dueño
        User creator = project.getCreator();
        if (!creator.getId().equals(userId)) {
            throw new ProjectAccessDeniedException("No tienes permiso para publicar este proyecto");

        }

        // Error 409: solo se puede publicar desde DRAFT
        if (!STATUS_DRAFT.equals(project.getStatus())) {
            throw new ProjectLockedException("El proyecto ya fue publicado y no está en estado borrador");

        }

        // Error 400: campos obligatorios (CA1) acumulando todos los faltantes
        List<String> missing = new ArrayList<>();
        if (project.getTitle() == null || project.getTitle().isBlank()) {
            missing.add("title");
        }
        if (project.getDescription() == null || project.getDescription().isBlank()) {
            missing.add("description");
        }
        if (project.getFundingGoal() == null) {
            missing.add("fundingGoal");
        }
        if (project.getDeadline() == null) {
            missing.add("deadline");
        }

        if (!missing.isEmpty()) {
            throw new MissingProjectFieldsException(
                    "Faltan campos obligatorios para publicar el proyecto", missing);
        }

        // Error 422: si falla, se lanza RiskScoreCalculationException y el proyecto no
        // se toca
        int riskScore = scoringEngine.calculateRiskScore(project, creator);

        // Solo ahora se modifica el proyecto
        project.setRiskScore(riskScore);
        project.setStatus(STATUS_ACTIVE);
        Project saved = projectRepository.saveAndFlush(project);

        return new PublishProjectResponse(
                saved.getId(),
                saved.getTitle(),
                saved.getStatus(),
                saved.getRiskScore(),
                resolveRiskTag(project.getRiskScore()),
                saved.getFundingGoal(),
                saved.getDeadline(),
                saved.getUpdatedAt()

        );
    }

    private String resolveRiskTag(int score) {
        if (score <= 1) {
            return "Riesgo Alto";
        } else if (score <= 5 && score >= 2) {
            return "Riesgo Medio";
        } else {
            return "Riesgo Bajo";
        }
    }
}