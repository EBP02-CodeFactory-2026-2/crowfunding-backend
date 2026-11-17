package com.crowdfunding.backend.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.crowdfunding.backend.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, UUID>{
    public List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);
    public boolean existsByUserIdAndTypeAndProjectId(Long userId, String type, UUID projectId);

}
