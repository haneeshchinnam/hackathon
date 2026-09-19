package com.example.detection.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "customers")
@Getter @Setter @NoArgsConstructor
public class Customer {
    @Id
    private String id;
    private String firstName;
    private String lastName;
    private java.time.LocalDate dateOfBirth;
    private String email;
    private String phoneNumber;
    private String city;
    private String state;
    private String country;
    private String postalCode;
    private String occupation;
    private java.math.BigDecimal annualIncome;
    private java.time.LocalDate customerSince;
    private String customerSegment;
    private String kycStatus;
    private String riskRating;
    private boolean politicallyExposed;
}
