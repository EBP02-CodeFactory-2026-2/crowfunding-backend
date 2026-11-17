package com.crowdfunding.backend.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
import com.crowdfunding.backend.persistence.UserRepository;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final static String PROJECT_FUNDED = "PROJECT_FUNDED";
    private final static String PROJECT_FAILED = "PROJECT_FAILED";
    private final static String REFUND_PROCESSED = "REFUND_PROCESSED";

    public NotificationService(
            NotificationRepository notificationRepository,
            UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    public List<NotificationResponse> getMyNotifications(String userEmail) {
        if (userEmail == null || userEmail.isBlank()) {
            throw new IllegalArgumentException("El email del usuario no puede ser nulo ni vacío.");
        }

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Usuario autenticado no encontrado: " + userEmail));

        return notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(NotificationResponse::fromEntity).toList();
    }

    public void markAsRead(UUID notificationId, String userEmail) {
        if (notificationId == null || userEmail == null || userEmail.isBlank()) {
            throw new IllegalArgumentException("El ID de notificación y el email de usuario son obligatorios.");
        }

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Usuario autenticado no encontrado: " + userEmail));

        Notification result = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("La notificación no fue encontrada."));

        if (!result.getUser().getId().equals(user.getId())) {
            throw new ProjectAccessDeniedException("Esta notificación pertenece a otro usuario.");
        }

        result.setIsRead(true);
        notificationRepository.saveAndFlush(result);

    }

    public void notifyProjectFunded(User recipient, Project project, long backerCount, LocalDateTime closedAt) {
        if (recipient == null || recipient.getId() == null
                || project == null || project.getId() == null
                || project.getTitle() == null || project.getCurrentAmount() == null
                || project.getFundingGoal() == null || backerCount < 0 || closedAt == null) {
            throw new IllegalArgumentException("El usuario y el proyecto deben estar registrados.");
        }
        
        // Valida que la notificación no exista ya para evitar duplicados
        if (notificationRepository.existsByUserIdAndTypeAndProjectId(
                recipient.getId(), PROJECT_FUNDED, project.getId())) {
            return;
        }

        Notification notification = new Notification();
        notification.setUser(recipient);
        notification.setProject(project);
        notification.setType(PROJECT_FUNDED);
        notification.setMessage(String.format(
                "¡El proyecto %s finalizó con éxito! Monto final recaudado: $%s. "
                        + "Meta: $%s. Patrocinadores: %d. Fecha de cierre: %s.",
                project.getTitle(),
                NumberFormat.getNumberInstance(Locale.US).format(project.getCurrentAmount()),
                NumberFormat.getNumberInstance(Locale.US).format(project.getFundingGoal()),
                backerCount,
                DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(closedAt)));
        notificationRepository.save(notification);

    }

    public void notifyProjectFailed(User recipient, Project project, long backerCount, LocalDateTime closedAt) {
        if (recipient == null || recipient.getId() == null
                || project == null
                || project.getId() == null
                || project.getTitle() == null
                || project.getCurrentAmount() == null
                || project.getFundingGoal() == null
                || project.getFundingGoal().signum() <= 0
                || backerCount < 0
                || closedAt == null) {
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
                "El proyecto %s caducó. Recaudó $%s de una meta de $%s (%s%% alcanzado). "
                        + "Patrocinadores: %d. Fecha de cierre: %s. "
                        + "Los aportes serán reembolsados y los patrocinadores pueden consultar su estado "
                        + "en el historial de aportes.",
                project.getTitle(),
                NumberFormat.getNumberInstance(Locale.US).format(project.getCurrentAmount()),
                NumberFormat.getNumberInstance(Locale.US).format(project.getFundingGoal()),
                project.getCurrentAmount().multiply(BigDecimal.valueOf(100))
                        .divide(project.getFundingGoal(), 2, RoundingMode.HALF_UP)
                        .stripTrailingZeros().toPlainString(),
                backerCount,
                DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(closedAt)));
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
