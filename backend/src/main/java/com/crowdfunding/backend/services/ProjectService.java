package com.crowdfunding.backend.services;

import com.crowdfunding.backend.entity.ContributionStatus;
import com.crowdfunding.backend.entity.Project;
import com.crowdfunding.backend.entity.ProjectStatus;
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
    public List<ProjectListResponse> getProjects(List<String> riskTags) {
        if (riskTags == null || riskTags.isEmpty()) {
            return projectRepository.findActiveProjectsByRiskTags(true, false, false, false)
                    .stream()
                    .map(project -> {
                        int backersCount = contributionRepository.countByProjectIdAndStatus(project.getId(),
                                ContributionStatus.COMPLETED);
                        return ProjectListResponse.fromEntity(project, backersCount);
                    })
                    .toList();
        }

        List<String> normalizedTags = riskTags.stream()
                .map(String::trim)
                .map(String::toUpperCase)
                .toList();

        boolean checkAlto = normalizedTags.contains("ALTO") || normalizedTags.contains("RIESGO ALTO");
        boolean checkMedio = normalizedTags.contains("MEDIO") || normalizedTags.contains("RIESGO MEDIO");
        boolean checkBajo = normalizedTags.contains("BAJO") || normalizedTags.contains("RIESGO BAJO");

        boolean filterAll = !checkAlto && !checkMedio && !checkBajo;

        return projectRepository.findActiveProjectsByRiskTags(filterAll, checkAlto, checkMedio, checkBajo)
                .stream()
                .map(project -> {
                    int backersCount = contributionRepository.countByProjectIdAndStatus(project.getId(),
                            ContributionStatus.COMPLETED);
                    return ProjectListResponse.fromEntity(project, backersCount);
                })
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
                    int backersCount = contributionRepository.countByProjectIdAndStatus(project.getId(),
                            ContributionStatus.COMPLETED);
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

        User creator = project.getCreator();
        if (!creator.getId().equals(userId)) {
            throw new ProjectAccessDeniedException("No tienes permiso para publicar este proyecto");
        }

        if (!ProjectStatus.DRAFT.equals(project.getStatus())) {
            throw new ProjectLockedException("El proyecto ya fue publicado y no está en estado borrador");
        }

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

        int riskScore = scoringEngine.calculateRiskScore(project, creator);

        project.setRiskScore(riskScore);
        project.setStatus(ProjectStatus.ACTIVE);
        Project saved = projectRepository.saveAndFlush(project);

        return new PublishProjectResponse(
                saved.getId(),
                saved.getTitle(),
                saved.getStatus().toString(),
                saved.getRiskScore(),
                saved.getRiskTag(),
                saved.getFundingGoal(),
                saved.getDeadline(),
                saved.getUpdatedAt());
    }

    public void deleteProject(UUID projectId) {
        String userEmail = SecurityContextHolder.getContext().getAuthentication().getName();

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Usuario autenticado no encontrado: " + userEmail));

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Proyecto no encontrado con el ID: " + projectId));

        User creator = project.getCreator();
        if (!creator.getId().equals(user.getId())) {
            throw new ProjectAccessDeniedException("No tienes permiso para eliminar este proyecto");
        }

        int hasContributions = contributionRepository.countByProjectIdAndStatus(projectId,
                ContributionStatus.COMPLETED);
        if (hasContributions > 0) {
            throw new DeleteConflictException("No se puede eliminar un proyecto con contribuciones");
        }

        project.setStatus(ProjectStatus.DELETED);
        projectRepository.save(project);
    }

    public EditProjectResponse editProject(UUID projectId, EditProjectRequest request) {
        String userEmail = SecurityContextHolder.getContext().getAuthentication().getName();

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Usuario autenticado no encontrado: " + userEmail));

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Proyecto no encontrado con el ID: " + projectId));

        User creator = project.getCreator();
        if (!creator.getId().equals(user.getId())) {
            throw new ProjectAccessDeniedException("No tienes permiso para publicar este proyecto");
        }

        boolean isActive = ProjectStatus.ACTIVE.equals(project.getStatus());

        if (!ProjectStatus.DRAFT.equals(project.getStatus())) {
            if (request.fundingGoal() == null || request.deadline() == null) {
                throw new ProjectLockedException(
                        "La meta y el plazo no pueden modificarse una vez el proyecto está activo");
            }
        }

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
        if (request.fundingGoal() != null) {
            project.setFundingGoal(request.fundingGoal());
        }
        if (request.deadline() != null) {
            project.setDeadline(request.deadline());
        }

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
