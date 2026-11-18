package com.crowdfunding.backend.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.crowdfunding.backend.entity.Transaction;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    boolean existsByNonce(String nonce);

    Optional<Transaction> findByContributionId(UUID contributionId);
}