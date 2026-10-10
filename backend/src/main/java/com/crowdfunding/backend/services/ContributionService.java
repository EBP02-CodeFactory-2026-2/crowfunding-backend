package com.crowdfunding.backend.services;

import com.crowdfunding.backend.persistence.TransactionRepository;
import com.crowdfunding.backend.persistence.UserRepository;

import java.util.UUID;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.crowdfunding.backend.contribution.ContributionRequest;
import com.crowdfunding.backend.contribution.ContributionResponse;
import com.crowdfunding.backend.entity.Contribution;
import com.crowdfunding.backend.entity.Project;
import com.crowdfunding.backend.entity.Transaction;
import com.crowdfunding.backend.exception.ProjectLockedException;
import com.crowdfunding.backend.exception.ReplayAttackException;
import com.crowdfunding.backend.exception.ResourceNotFoundException;
import com.crowdfunding.backend.persistence.ContributionRepository;
import com.crowdfunding.backend.persistence.ProjectRepository;

@Service 
public class ContributionService {
    private final TransactionRepository transactionRepository;
    final ContributionRepository contributionRepository;
    final ProjectRepository projectRepository;
    final UserRepository userRepository;

    ContributionService(ContributionRepository contributionRepository, ProjectRepository projectRepository, TransactionRepository transactionRepository, UserRepository userRepository) {
        this.contributionRepository = contributionRepository;
        this.projectRepository = projectRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
    }

    @Transactional 
    public ContributionResponse supportProject(UUID projectId, ContributionRequest request){
        String userEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        
        Project project = projectRepository.findById(projectId).orElseThrow(() -> new ResourceNotFoundException("Proyecto no encontrado con el ID: " + projectId));

        if (!"ACTIVE".equals(project.getStatus())){
            throw new ProjectLockedException("El proyecto no se encuentra en estado activo y no puede recibir aportes");
        }

        if (transactionRepository.existsByNonce(request.nonce())){
            throw new ReplayAttackException("El token de seguridad (nonce) ya ha sido utilizado en una transacción anterior");
        }

        Contribution contribution = new Contribution();
        contribution.setBacker(userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario autenticado no encontrado: " + userEmail)));
        contribution.setProject(project);
        contribution.setAmount(request.amount());
        contribution.setStatus("COMPLETED"); // el mock de pasarela siempre será exitoso en este sprint

        contributionRepository.save(contribution);

        Transaction transaction = new Transaction();
        transaction.setContribution(contribution);
        transaction.setReferenceCode("MOCK-"+ UUID.randomUUID());
        transaction.setAmount(request.amount());
        transaction.setPaymentMethod(request.paymentMethod());
        transaction.setStatus("SUCCESS"); 
        transaction.setNonce(request.nonce());

        transactionRepository.save(transaction);

        projectRepository.incrementCurrentAmount(projectId, request.amount());

        project.setCurrentAmount(project.getCurrentAmount().add(request.amount()));

        return ContributionResponse.fromEntity(contribution, transaction, project);
    }
}
