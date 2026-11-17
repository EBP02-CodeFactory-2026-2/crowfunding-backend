package com.crowdfunding.backend.notification;

import java.time.LocalDateTime;
import java.util.UUID;

import com.crowdfunding.backend.entity.Notification;

public record NotificationResponse(
        UUID id,
        String type,
        String message,
        Boolean isRead,
        LocalDateTime createdAt) {
    public static NotificationResponse fromEntity(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getMessage(),
                notification.getIsRead(),
                notification.getCreatedAt());
    }
}
