package com.crowdfunding.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contribution_id", nullable = false)
    private Contribution contribution;

    @Column(name = "reference_code")
    private String referenceCode;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String status; // SUCCESS, DECLINED, ERROR

    @Column(name = "processed_at")
    private LocalDateTime processedAt;
}