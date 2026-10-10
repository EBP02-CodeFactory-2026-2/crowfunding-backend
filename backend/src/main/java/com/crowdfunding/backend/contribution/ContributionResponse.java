package com.crowdfunding.backend.contribution;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.crowdfunding.backend.entity.Contribution;
import com.crowdfunding.backend.entity.Project;
import com.crowdfunding.backend.entity.Transaction;

public record ContributionResponse(
    UUID contributionId,
    UUID projectId,
    BigDecimal amount,
    String status,
    TransactionDTO transaction,
    BigDecimal projectNewAmount,
    LocalDateTime createdAt
) {
    public record  TransactionDTO(
        String referenceCode,
        String paymentMethod,
        LocalDateTime processedAt
    ) {}

    public static ContributionResponse fromEntity(Contribution contribution, Transaction transaction, Project project){
        TransactionDTO transactionDTO = new TransactionDTO(
            transaction.getReferenceCode(),
            transaction.getPaymentMethod(),
            transaction.getProcessedAt()
        );
        return new ContributionResponse(
            contribution.getId(), 
            project.getId(), 
            contribution.getAmount(), 
            contribution.getStatus(), 
            transactionDTO, 
            project.getCurrentAmount(), 
            contribution.getCreatedAt()
        );
    }
}
