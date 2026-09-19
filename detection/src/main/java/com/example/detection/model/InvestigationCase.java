package com.example.detection.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "investigation_cases")
@Getter @Setter @NoArgsConstructor
public class InvestigationCase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;
    private String title;
    private String status;
    private String assignedTo;
    private String dispositionReason;
    private java.time.Instant createdAt;
    private java.time.Instant updatedAt;
}
