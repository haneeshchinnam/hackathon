package com.example.detection.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "accounts")
@Getter @Setter @NoArgsConstructor
public class Account {
    @Id
    private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;
    private String accountType;
    private String accountStatus;
    private String currency;
    private java.time.LocalDate openDate;
    private java.time.LocalDate closeDate;
    private String branchCode;
    private String branchCity;
    private java.math.BigDecimal currentBalance;
    private String riskRating;
}
