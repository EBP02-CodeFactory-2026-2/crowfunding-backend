package com.crowdfunding.backend.services;

import com.crowdfunding.backend.entity.Contribution;
import com.crowdfunding.backend.entity.Project;
import com.crowdfunding.backend.entity.User;
import com.crowdfunding.backend.exception.DeleteConflictException;
import com.crowdfunding.backend.exception.MissingProjectFieldsException;
import com.crowdfunding.backend.exception.ProjectAccessDeniedException;
import com.crowdfunding.backend.exception.ProjectLockedException;
import com.crowdfunding.backend.exception.ResourceNotFoundException;
import com.crowdfunding.backend.exception.RiskScoreCalculationException;
import com.crowdfunding.backend.persistence.ContributionRepository;
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
    private final ContributionRepository contributionRepository;
    private final ScoringEngine scoringEngine;
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_ACTIVE = "ACTIVE";

    public ProjectService(ProjectRepository projectRepository,
            UserRepository userRepository,
            ContributionRepository contributionRepository,
            ScoringEngine scoringEngine) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.contributionRepository = contributionRepository;
        this.scoringEngine = scoringEngine;
    }

    @Transactional(readOnly = true)
    public List<ProjectListResponse> getAllProjects() {
        return projectRepository.findByStatus(STATUS_ACTIVE)
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

    @Transactional(readOnly = true)
    public List<ProjectListResponse> getMyProjects() {
        String userEmail = SecurityContextHolder.getContext().getAuthentication().getName();

        User creator = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario autenticado no encontrado: " + userEmail));

        return projectRepository.findByCreatorId(creator.getId())
                .stream()
                .map(project -> {
                    int backersCount = contributionRepository.countByProjectIdAndStatus(project.getId(), "COMPLETED");
                    return ProjectListResponse.fromEntity(project, backersCount);
                })
                .toList();
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
                saved.getUpdatedAt());
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

    public void deleteProject(UUID projectId) {
        String userEmail = SecurityContextHolder.getContext().getAuthentication().getName();

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Usuario autenticado no encontrado: " + userEmail));

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Proyecto no encontrado con el ID: " + projectId));

        // Error 403: el usuario autenticado no es el dueño
        User creator = project.getCreator();
        if (!creator.getId().equals(user.getId())) {
            throw new ProjectAccessDeniedException("No tienes permiso para eliminar este proyecto");
        }

        // Error 409: Conflict
        int hasContributions = contributionRepository.countByProjectIdAndStatus(projectId, "COMPLETED");
        if (hasContributions > 0) {
            throw new DeleteConflictException("No se puede eliminar un proyecto con contribuciones");
        }

        project.setStatus("DELETED");
        projectRepository.save(project);

    }

    public EditProjectResponse editProject(UUID projectId, EditProjectRequest request) {
        String userEmail = SecurityContextHolder.getContext().getAuthentication().getName();

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Usuario autenticado no encontrado: " + userEmail));

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Proyecto no encontrado con el ID: " + projectId));

        // Error 403: el usuario autenticado no es el dueño
        User creator = project.getCreator();
        if (!creator.getId().equals(user.getId())) {
            throw new ProjectAccessDeniedException("No tienes permiso para publicar este proyecto");
        }

        boolean isActive = STATUS_ACTIVE.equals(project.getStatus());

        if (!STATUS_DRAFT.equals(project.getStatus())) {
            if (request.fundingGoal() == null || request.deadline() == null) {
                throw new ProjectLockedException(
                        "La meta y el plazo no pueden modificarse una vez el proyecto está activo");

            }

        }

        // Aplicar solo los campos enviados
        boolean contentChanged = false;

        if (request.title() != null) {
            project.setTitle(request.title());
            contentChanged = true;
        }
        if (request.description() != null) {
            project.setDescription(request.description());
            contentChanged = true;
        }
        if (request.imageUrl() != null) {
            project.setImageUrl(request.imageUrl());
            contentChanged = true;
        }
        if (request.fundingGoal() != null) { // solo llega aquí si es DRAFT
            project.setFundingGoal(request.fundingGoal());
        }
        if (request.deadline() != null) { // solo llega aquí si es DRAFT
            project.setDeadline(request.deadline());
        }

        // CA5: recalcular riesgo si se editó contenido en un proyecto activo
        if (isActive && contentChanged) {
            try {
                project.setRiskScore(scoringEngine.calculateRiskScore(project, creator));
            } catch (Exception e) {
                throw new RiskScoreCalculationException(
                        "No fue posible recalcular el riesgo del proyecto: " + e.getMessage(),
                    new ArrayList<>());
            }
        }

        Project saved = projectRepository.saveAndFlush(project);
        return EditProjectResponse.fromEntity(saved);
    }

}
