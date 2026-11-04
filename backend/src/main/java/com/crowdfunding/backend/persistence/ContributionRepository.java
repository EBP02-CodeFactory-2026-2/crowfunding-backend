package com.crowdfunding.backend.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import com.crowdfunding.backend.entity.Contribution;
import com.crowdfunding.backend.entity.ContributionStatus;

public interface ContributionRepository extends JpaRepository<Contribution, UUID>{
    // Para (HU-1.1.6): Cuenta cuántos aportes COMPLETED tiene un proyecto
    public int countByProjectIdAndStatus(UUID projectId, ContributionStatus status);
}
