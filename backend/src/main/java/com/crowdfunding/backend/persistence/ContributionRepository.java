package com.crowdfunding.backend.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import com.crowdfunding.backend.entity.Contribution;
import com.crowdfunding.backend.entity.ContributionStatus;

public interface ContributionRepository extends JpaRepository<Contribution, UUID> {
    // Para (HU-1.1.6): Cuenta cuántos aportes COMPLETED tiene un proyecto
    public int countByProjectIdAndStatus(UUID projectId, ContributionStatus status);

    @EntityGraph(attributePaths = "project")
    List<Contribution> findByBackerIdOrderByCreatedAtDesc(UUID backerId);

    @EntityGraph(attributePaths = "project")
    List<Contribution> findByBackerIdAndStatusOrderByCreatedAtDesc(UUID backerId, ContributionStatus status);

    // Para HU-4.1.4 CA4: Idempotencia de reembolsos
    boolean existsByIdAndStatus(java.util.UUID id, ContributionStatus status);

    // Para HU-4.1.4 CA1: Obtener aportes COMPLETED de un proyecto (para reembolsar)
    List<Contribution> findByProjectIdAndStatus(java.util.UUID projectId, ContributionStatus status);
}
