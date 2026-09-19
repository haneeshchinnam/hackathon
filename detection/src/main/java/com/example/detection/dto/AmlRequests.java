package com.example.detection.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public final class AmlRequests {
    private AmlRequests() {}
    public record CustomerInput(
        @NotBlank @Size(max=64) String id,
        @NotBlank @Size(max=100) String firstName, @NotBlank @Size(max=100) String lastName,
        @Past LocalDate dateOfBirth, @Email @Size(max=254) String email, @Size(max=32) String phoneNumber,
        @Size(max=100) String city, @Size(max=100) String state, @NotNull @Pattern(regexp="[A-Z]{2}") String country,
        @Size(max=20) String postalCode, @Size(max=100) String occupation,
        @PositiveOrZero @Digits(integer=22,fraction=2) BigDecimal annualIncome,
        @NotNull @PastOrPresent LocalDate customerSince, @Size(max=40) String customerSegment,
        @NotBlank @Size(max=30) String kycStatus, @NotNull @Pattern(regexp="LOW|MEDIUM|HIGH") String riskRating,
        boolean politicallyExposed) {}
    public record AccountInput(
        @NotBlank @Size(max=64) String id, @NotBlank @Size(max=64) String customerId,
        @NotBlank @Size(max=30) String accountType, @NotNull @Pattern(regexp="ACTIVE|INACTIVE|DORMANT|FROZEN|CLOSED") String accountStatus,
        @NotNull @Pattern(regexp="[A-Z]{3}") String currency, @NotNull @PastOrPresent LocalDate openDate, @PastOrPresent LocalDate closeDate,
        @Size(max=30) String branchCode, @Size(max=100) String branchCity,
        @NotNull @Digits(integer=22,fraction=2) BigDecimal currentBalance,
        @NotNull @Pattern(regexp="LOW|MEDIUM|HIGH") String riskRating) {}
    public record TransactionInput(
        @NotBlank @Size(max=64) String id, @NotBlank @Size(max=64) String accountId,
        @NotNull @DecimalMin("0.01") @Digits(integer=16,fraction=2) BigDecimal amount,
        @NotNull @Pattern(regexp="[A-Z]{3}") String currency, @NotNull @Pattern(regexp="IN|OUT") String direction,
        @NotBlank @Size(max=200) String counterparty, @NotBlank @Size(max=30) String channel,
        @NotNull @PastOrPresent Instant occurredAt, @NotNull @Pattern(regexp="[A-Z]{2}") String jurisdiction) {}
    public record Batch<T>(@NotEmpty @Size(max=10000) List<@NotNull @Valid T> records) {}
    public record RuleInput(boolean enabled, @NotNull @Positive @Digits(integer=16,fraction=4) BigDecimal threshold,
        @NotNull @Positive @Digits(integer=16,fraction=4) BigDecimal secondaryThreshold,
        @Min(1) @Max(2160) int windowHours, @Min(1) @Max(10000) int minimumCount, @Min(1) @Max(100) int weight) {}
    public record RateInput(@NotNull @Positive @Digits(integer=12,fraction=8) BigDecimal inrPerUnit) {}
    public record WatchInput(@NotNull @Pattern(regexp="JURISDICTION|COUNTERPARTY") String kind,
        @NotBlank @Size(max=200) String value, boolean enabled) {}
    public record Disposition(@NotNull @Pattern(regexp="IN_REVIEW|CLEARED|ESCALATED") String status,
        @NotBlank @Size(max=2000) String reason) {}
    public record CaseInput(@NotBlank @Size(max=200) String title,
        @NotEmpty @Size(max=100) List<@NotNull Long> alertIds, @Size(max=20) String assignedTo) {}
    public record CaseUpdate(@NotNull @Pattern(regexp="OPEN|IN_PROGRESS|CLOSED") String status,
        @NotBlank @Size(max=2000) String reason, @Size(max=20) String assignedTo) {}
}
