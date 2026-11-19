package com.crowdfunding.backend.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.crowdfunding.backend.entity.Contribution;
import com.crowdfunding.backend.entity.ContributionStatus;

public interface ContributionRepository extends JpaRepository<Contribution, UUID> {

    // HU-1.1.6: cuenta cuántos aportes COMPLETED tiene un proyecto
    int countByProjectIdAndStatus(UUID projectId, ContributionStatus status);

    @EntityGraph(attributePaths = "project")
    List<Contribution> findByBackerIdOrderByCreatedAtDesc(Long backerId);

    @EntityGraph(attributePaths = "project")
    List<Contribution> findByBackerIdAndStatusOrderByCreatedAtDesc(Long backerId, ContributionStatus status);

    // Recibo: solo devuelve el aporte si pertenece al usuario autenticado (protección IDOR)
    @EntityGraph(attributePaths = "project")
    Optional<Contribution> findByIdAndBackerId(UUID id, Long backerId);

    // HU-4.1.4 CA4: idempotencia de reembolsos
    boolean existsByIdAndStatus(UUID id, ContributionStatus status);

    // HU-4.1.4 CA1: aportes COMPLETED de un proyecto (para reembolsar)
    List<Contribution> findByProjectIdAndStatus(UUID projectId, ContributionStatus status);
}