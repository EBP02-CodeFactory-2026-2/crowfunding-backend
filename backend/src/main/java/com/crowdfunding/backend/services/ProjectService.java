package com.crowdfunding.backend.services;
import com.crowdfunding.backend.entity.Project;
import com.crowdfunding.backend.entity.User;
import com.crowdfunding.backend.persistence.ProjectRepository;
import com.crowdfunding.backend.persistence.UserRepository;
import com.crowdfunding.backend.project.CreateProjectRequest;
import com.crowdfunding.backend.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    public ProjectService(ProjectRepository projectRepository, UserRepository userRepository) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
    }

    public Project createProject(CreateProjectRequest request) {
        if (request.getGoalAmount() == null || request.getGoalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto meta debe ser mayor a cero");
        }

        User creator = userRepository.findById(request.getCreatorId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario creador no encontrado"));

        Project project = new Project();
        project.setTitle(request.getTitle());
        project.setDescription(request.getDescription());
        project.setFundingGoal(request.getGoalAmount());
        project.setCurrentAmount(BigDecimal.ZERO);
        project.setDeadline(request.getDeadline().atTime(23, 59, 59));
        project.setCreator(creator);

        return projectRepository.save(project);
    }
    
}
