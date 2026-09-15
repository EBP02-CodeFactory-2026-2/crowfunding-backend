package com.crowdfunding.backend.services;

import com.crowdfunding.backend.entity.Project;
import com.crowdfunding.backend.entity.User;
import com.crowdfunding.backend.exception.ResourceNotFoundException;
import com.crowdfunding.backend.persistence.ProjectRepository;
import com.crowdfunding.backend.persistence.UserRepository;
import com.crowdfunding.backend.project.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    public ProjectService(ProjectRepository projectRepository, UserRepository userRepository) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
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
}