package com.crowdfunding.backend.persistence;

import com.crowdfunding.backend.entity.Project;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    @Transactional
    @Modifying(clearAutomatically = true) // Indica que se alteran los datos y limpia el caché de persistencia después de ejecutarla
    @Query("UPDATE Project p SET p.currentAmount = p.currentAmount + :amount WHERE p.id = :projectId")
    int incrementCurrentAmount(@Param("projectId") UUID projectId, @Param("amount") BigDecimal amount);
    // el retorno de int indica la cantidad de filas modificadas
    List<Project> findByCreatorId(UUID creatorId);
}