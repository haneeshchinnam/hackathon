package com.example.detection.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "alerts")
@Getter @Setter @NoArgsConstructor
public class Alert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;
    private String status;
    private int riskScore;
    @Column(columnDefinition = "text")
    private String explanation;
    private String dispositionReason;
    private String analyst;
    private java.time.Instant createdAt;
    private java.time.Instant updatedAt;
}
