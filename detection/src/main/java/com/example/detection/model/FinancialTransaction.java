package com.example.detection.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "transactions")
@Getter @Setter @NoArgsConstructor
public class FinancialTransaction {
    @Id
    private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;
    private java.math.BigDecimal amount;
    private String currency;
    private java.math.BigDecimal exchangeRate;
    private java.math.BigDecimal baseAmount;
    private java.math.BigDecimal usdAmount;
    private String direction;
    private String counterparty;
    private String channel;
    private java.time.Instant occurredAt;
    private String jurisdiction;
    private java.time.Instant ingestedAt;
}
