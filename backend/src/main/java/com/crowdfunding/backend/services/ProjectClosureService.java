package com.crowdfunding.backend.services;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.crowdfunding.backend.entity.Contribution;
import com.crowdfunding.backend.entity.ContributionStatus;
import com.crowdfunding.backend.entity.Project;
import com.crowdfunding.backend.entity.ProjectStatus;
import com.crowdfunding.backend.persistence.ContributionRepository;
import com.crowdfunding.backend.persistence.ProjectRepository;

/**
 * Servicio dedicado al cierre automático de proyectos y procesamiento de reembolsos.
 * Responsabilidades:
 *   - HU-4.1.3: Cerrar proyectos por meta alcanzada o plazo vencido.
 *   - HU-4.1.4: Reembolsar aportes de proyectos caducados.
 *   - HU-4.1.1/4.1.2: Generar notificaciones de cierre.
 */
@Service
public class ProjectClosureService {
    private final ProjectRepository projectRepository;
    private final ContributionRepository contributionRepository;
    // TODO: inyectar NotificationRepository cuando se cree la entidad

    public ProjectClosureService(
            ProjectRepository projectRepository,
            ContributionRepository contributionRepository) {
        this.projectRepository = projectRepository;
        this.contributionRepository = contributionRepository;
    }

    /**
     * Evalúa un proyecto y lo cierra si corresponde.
     * - Si currentAmount >= fundingGoal -> FUNDED
     * - Si deadline <= now -> FAILED
     * 
     * @return true si el proyecto fue cerrado, false si sigue ACTIVE
     */
    @Transactional
    public boolean evaluateAndClose(Project project, LocalDateTime now) {
        if (project.getStatus() != ProjectStatus.ACTIVE) {
            return false;
        }

        // CA1: Cierre por meta alcanzada
        if (project.getCurrentAmount().compareTo(project.getFundingGoal()) >= 0) {
            project.setStatus(ProjectStatus.FUNDED);
            projectRepository.save(project);
            // TODO: generar notificaciones (HU-4.1.1)
            return true;
        }

        // CA2: Cierre por plazo vencido
        if (project.getDeadline() != null && project.getDeadline().isBefore(now)) {
            project.setStatus(ProjectStatus.FAILED);
            projectRepository.save(project);
            processRefunds(project); // HU-4.1.4
            // TODO: generar notificaciones (HU-4.1.2)
            return true;
        }

        return false;
    }

    /**
     * Procesa reembolsos para todos los aportes COMPLETED de un proyecto caducado.
     * Idempotente: si un aporte ya está REFUNDED, no hace nada (HU-4.1.4 CA4).
     */
    @Transactional
    public void processRefunds(Project project) {
        List<Contribution> completedContributions = contributionRepository
            .findByProjectIdAndStatus(project.getId(), ContributionStatus.COMPLETED);
        
        for (Contribution contribution : completedContributions) {
            contribution.setStatus(ContributionStatus.REFUNDED);
            contributionRepository.save(contribution);
            // TODO: generar notificación de reembolso al backer (HU-4.1.4 CA2)
        }
    }

    /**
     * Método para ser invocado por un @Scheduled job.
     * Busca proyectos ACTIVE con plazo vencido y los cierra.
     */
    @Transactional
    public int processExpiredProjects() {
        LocalDateTime now = LocalDateTime.now();
        List<Project> expiredProjects = projectRepository.findExpiredActiveProjects(now);
        int closed = 0;
        for (Project project : expiredProjects) {
            if (evaluateAndClose(project, now)) {
                closed++;
            }
        }
        return closed;
    }
}
