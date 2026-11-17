package com.crowdfunding.backend.services;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.crowdfunding.backend.entity.Contribution;
import com.crowdfunding.backend.entity.ContributionStatus;
import com.crowdfunding.backend.entity.Project;
import com.crowdfunding.backend.entity.ProjectStatus;
import com.crowdfunding.backend.entity.User;
import com.crowdfunding.backend.persistence.ContributionRepository;

import com.crowdfunding.backend.persistence.ProjectRepository;

/**
 * Servicio dedicado al cierre automático de proyectos y procesamiento de
 * reembolsos.
 * Responsabilidades:
 * - HU-4.1.3: Cerrar proyectos por meta alcanzada o plazo vencido.
 * - HU-4.1.4: Reembolsar aportes de proyectos caducados.
 * - HU-4.1.1/4.1.2: Generar notificaciones de cierre.
 */
@Service
public class ProjectClosureService {
    private final ProjectRepository projectRepository;
    private final ContributionRepository contributionRepository;
    private final NotificationService notificationService;

    public ProjectClosureService(ProjectRepository projectRepository, ContributionRepository contributionRepository,
            NotificationService notificationService) {
        this.projectRepository = projectRepository;
        this.contributionRepository = contributionRepository;
        this.notificationService = notificationService;
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
            // HECHO: generar notificaciones (HU-4.1.1)
            List<Contribution> completedContributions = contributionRepository.findByProjectIdAndStatus(
                    project.getId(), ContributionStatus.COMPLETED);
            long backerCount = completedContributions.stream()
                    .map(contribution -> contribution.getBacker().getId())
                    .distinct()
                    .count();

            User creator = project.getCreator();
            Set<Long> notifiedUserIds = new HashSet<>();

            notificationService.notifyProjectFunded(creator, project, backerCount, now);
            notifiedUserIds.add(creator.getId());

            for (Contribution contribution : completedContributions) {
                User backer = contribution.getBacker();
                if (notifiedUserIds.add(backer.getId())) {
                    notificationService.notifyProjectFunded(backer, project, backerCount, now);
                }
            }
            return true;
        }

        // CA2: Cierre por plazo vencido
        if (project.getDeadline() != null && project.getDeadline().isBefore(now)) {
            project.setStatus(ProjectStatus.FAILED);
            projectRepository.save(project);
            processRefunds(project); // HU-4.1.4
            // HECHO: generar notificaciones (HU-4.1.2)
            List<Contribution> refundedContributions = contributionRepository.findByProjectIdAndStatus(
                    project.getId(), ContributionStatus.REFUNDED);
            long backerCount = refundedContributions.stream()
                    .map(contribution -> contribution.getBacker().getId())
                    .distinct()
                    .count();

            User creator = project.getCreator();
            Set<Long> notifiedUserIds = new HashSet<>();

            notificationService.notifyProjectFailed(creator, project, backerCount, now);
            notifiedUserIds.add(creator.getId());

            for (Contribution contribution : refundedContributions) {
                User backer = contribution.getBacker();
                if (notifiedUserIds.add(backer.getId())) {
                    notificationService.notifyProjectFailed(backer, project, backerCount, now);
                }
            }
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
            if (contribution.getStatus() == ContributionStatus.REFUNDED) {
                continue;
            }
            contribution.setStatus(ContributionStatus.REFUNDED);
            contributionRepository.save(contribution);
            // HECHO: generar notificación de reembolso al backer (HU-4.1.4 CA2)
            notificationService.notifyRefundProcessed(contribution);
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

    @Scheduled(fixedRate = 300000) // se ejecuta cada 5 minutos
    public void scheduledProjectClosure() {
        processExpiredProjects();
    }
}
