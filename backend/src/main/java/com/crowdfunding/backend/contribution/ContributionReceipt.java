package com.crowdfunding.backend.contribution;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.crowdfunding.backend.entity.Contribution;
import com.crowdfunding.backend.entity.Project;
import com.crowdfunding.backend.entity.Transaction;

public record ContributionReceipt(
    String receiptId,
    UUID contributionId,
    String projectName,
    BigDecimal amount,
    String status,
    TransactionDTO transactionDetails,
    LocalDateTime generatedAt
) {
    public record TransactionDTO(
        String referenceCode,
        String paymentMethod,
        LocalDateTime processedAt
    ) {}

    public static ContributionReceipt fromEntity(Contribution contribution, Transaction transaction, Project project) {
        return new ContributionReceipt(
            "RCP-" + transaction.getReferenceCode(),
            contribution.getId(),
            project.getTitle(),
            contribution.getAmount(),
            contribution.getStatus().name(),
            new TransactionDTO(
                transaction.getReferenceCode(),
                maskPaymentMethod(transaction),
                transaction.getProcessedAt()
            ),
            LocalDateTime.now()
        );
    }

    // Últimos 4 dígitos simulados a partir del id de la transacción: parecen aleatorios,
    // pero son los mismos cada vez que se consulta el recibo de esa transacción
    private static String maskPaymentMethod(Transaction transaction) {
        int lastFour = Math.floorMod(transaction.getId().hashCode(), 10_000);
        return transaction.getPaymentMethod() + " *** " + String.format("%04d", lastFour);
    }
}