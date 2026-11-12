package com.crowdfunding.backend.persistence;

import com.crowdfunding.backend.entity.Project;
import com.crowdfunding.backend.entity.ProjectStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    @Transactional
    @Modifying(clearAutomatically = true) // Indica que se alteran los datos y limpia el caché de persistencia después
                                          // de ejecutarla
    @Query("UPDATE Project p SET p.currentAmount = p.currentAmount + :amount WHERE p.id = :projectId")
    int incrementCurrentAmount(@Param("projectId") UUID projectId, @Param("amount") BigDecimal amount);

    // Para HU-4.1.3 CA2: Cierre automático por plazo vencido
    @Query("SELECT p FROM Project p WHERE p.status = com.crowdfunding.backend.entity.ProjectStatus.ACTIVE AND p.deadline <= :now")
    List<Project> findExpiredActiveProjects(@Param("now") LocalDateTime now);

    // el retorno de int indica la cantidad de filas modificadas
    List<Project> findByCreatorId(Long creatorId);

    boolean existsByCreatorIdAndStatus(Long creatorId, ProjectStatus status);

    List<Project> findByStatus(ProjectStatus status);

    @Query("""
                SELECT p, COUNT(c.id)
                FROM Project p
                LEFT JOIN Contribution c ON p.id = c.project.id AND c.status = com.crowdfunding.backend.entity.ContributionStatus.COMPLETED
                WHERE p.status = com.crowdfunding.backend.entity.ProjectStatus.ACTIVE
                GROUP BY p.id
                ORDER BY p.createdAt DESC
            """)
    List<Object[]> findActiveProjectsWithBackersCount();

    @Query("""
        SELECT p FROM Project p 
        WHERE p.status = com.crowdfunding.backend.entity.ProjectStatus.ACTIVE 
        AND (:filterAll = true OR 
             (:checkAlto = true AND p.riskScore <= 1) OR 
             (:checkMedio = true AND p.riskScore BETWEEN 2 AND 5) OR 
             (:checkBajo = true AND p.riskScore BETWEEN 6 AND 9))
    """)
    List<Project> findActiveProjectsByRiskTags(
        @Param("filterAll") boolean filterAll,
        @Param("checkAlto") boolean checkAlto,
        @Param("checkMedio") boolean checkMedio,
        @Param("checkBajo") boolean checkBajo
    );
}