package com.crowdfunding.backend.services;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.crowdfunding.backend.entity.Contribution;
import com.crowdfunding.backend.entity.Notification;
import com.crowdfunding.backend.entity.Project;
import com.crowdfunding.backend.entity.User;
import com.crowdfunding.backend.exception.ProjectAccessDeniedException;
import com.crowdfunding.backend.exception.ResourceNotFoundException;
import com.crowdfunding.backend.notification.NotificationResponse;
import com.crowdfunding.backend.persistence.NotificationRepository;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final static String PROJECT_FUNDED = "PROJECT_FUNDED";
    private final static String PROJECT_FAILED = "PROJECT_FAILED";
    private final static String REFUND_PROCESSED = "REFUND_PROCESSED";

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public List<NotificationResponse> getMyNotifications(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("El ID de usuario no puede ser nulo.");
        }
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(NotificationResponse::fromEntity).toList();
    }

    public void markAsRead(UUID notificationId, Long userId) {
        if (notificationId == null || userId == null) {
            throw new IllegalArgumentException("El ID de usuario y/o de la notificación no puede ser nulo.");
        }

        Notification result = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("La notificación no fue encontrada."));

        if (!result.getUser().getId().equals(userId)) {
            throw new ProjectAccessDeniedException("Esta notificación pertenece a otro usuario.");
        }

        result.setIsRead(true);
        notificationRepository.saveAndFlush(result);

    }

    public void notifyProjectFunded(User recipient, Project project) {
        if (recipient == null || recipient.getId() == null
                || project == null || project.getId() == null
                || project.getTitle() == null || project.getCurrentAmount() == null) {
            throw new IllegalArgumentException("El usuario y el proyecto deben estar registrados.");
        }

        Notification notification = new Notification();
        notification.setUser(recipient);
        notification.setProject(project);
        notification.setType(PROJECT_FUNDED);
        notification.setMessage(String.format(
                "¡El proyecto %s finalizó con éxito! Monto final recaudado: $%s",
                project.getTitle(),
                NumberFormat.getNumberInstance(Locale.US).format(project.getCurrentAmount())));
        notificationRepository.save(notification);

    }

    public void notifyProjectFailed(User recipient, Project project) {
        if (recipient == null || recipient.getId() == null
                || project == null
                || project.getId() == null
                || project.getTitle() == null
                || project.getCurrentAmount() == null
                || project.getFundingGoal() == null) {
            throw new IllegalArgumentException("El usuario y el proyecto deben estar registrados.");
        }

        // Valida que la notificación no exista ya para evitar duplicados
        if (notificationRepository.existsByUserIdAndTypeAndProjectId(
                recipient.getId(), PROJECT_FAILED, project.getId())) {
            return;
        }

        Notification notification = new Notification();
        notification.setUser(recipient);
        notification.setProject(project);
        notification.setType(PROJECT_FAILED);
        notification.setMessage(String.format(
                "El proyecto %s caducó. Recaudó $%s de una meta de $%s. "
                        + "Los aportes serán reembolsados y los patrocinadores pueden consultar su estado "
                        + "en el historial de aportes.",
                project.getTitle(),
                NumberFormat.getNumberInstance(Locale.US).format(project.getCurrentAmount()),
                NumberFormat.getNumberInstance(Locale.US).format(project.getFundingGoal())));
        notificationRepository.save(notification);
    }

    public void notifyRefundProcessed(Contribution contribution) {
        if (contribution == null
                || contribution.getBacker() == null
                || contribution.getBacker().getId() == null
                || contribution.getProject() == null
                || contribution.getProject().getId() == null
                || contribution.getProject().getTitle() == null
                || contribution.getAmount() == null) {
            throw new IllegalArgumentException("El aporte debe incluir un usuario, un proyecto y un monto válidos.");
        }

        Notification notification = new Notification();
        notification.setUser(contribution.getBacker());
        notification.setProject(contribution.getProject());
        notification.setType(REFUND_PROCESSED);
        notification.setMessage(String.format(
                "Tu aporte de $%s al proyecto %s ha sido reembolsado",
                NumberFormat.getNumberInstance(Locale.US).format(contribution.getAmount()),
                contribution.getProject().getTitle()));
        notificationRepository.save(notification);
    }

}
