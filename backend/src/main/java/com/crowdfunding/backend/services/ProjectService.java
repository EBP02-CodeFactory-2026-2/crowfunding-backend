package com.crowdfunding.backend.services;

import com.crowdfunding.backend.entity.Project;
import com.crowdfunding.backend.entity.User;
import com.crowdfunding.backend.persistence.ProjectRepository;
import com.crowdfunding.backend.persistence.UserRepository;
import com.crowdfunding.backend.project.CreateProjectRequest;
import com.crowdfunding.backend.project.ProjectResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    public ProjectService(ProjectRepository projectRepository, UserRepository userRepository) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ProjectResponse createProject(CreateProjectRequest request) {
        String userEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        
        User creator = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuario autenticado no encontrado"));

        Project project = new Project();
        project.setTitle(request.title());
        project.setDescription(request.description());
        project.setFundingGoal(request.fundingGoal());
        project.setCurrentAmount(BigDecimal.ZERO);
        project.setImageUrl(request.imageUrl());
        project.setCreator(creator);

        Project savedProject = projectRepository.save(project);
        return ProjectResponse.fromEntity(savedProject);
    }
}