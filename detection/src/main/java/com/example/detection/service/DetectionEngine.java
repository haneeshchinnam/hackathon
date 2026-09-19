package com.example.detection.service;

import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Pure event-time rules. Amount comparisons use the rate snapshot captured on ingestion. */
@Component
public class DetectionEngine {
    public record Rule(String code, boolean enabled, BigDecimal threshold, BigDecimal secondaryThreshold,
                       int windowHours, int minimumCount, int weight) {}
    public record Event(String id, String accountId, BigDecimal baseAmount, BigDecimal usdAmount,
                        String direction, String counterparty, String jurisdiction, Instant time) {}
    public record Hit(Rule rule, List<String> evidence, String explanation) {}

    public List<Hit> evaluate(Event current, List<Event> history, List<Rule> rules, Set<String> watchlist) {
        List<Hit> hits = new ArrayList<>();
        for (Rule rule : rules) {
            if (!rule.enabled()) continue;
            List<Event> window = history.stream().filter(e -> e.accountId().equals(current.accountId())
                && !e.time().isAfter(current.time())
                && !e.time().isBefore(current.time().minus(Duration.ofHours(rule.windowHours())))).toList();
            switch (rule.code()) {
                case "THRESHOLD" -> {
                    if (current.usdAmount().compareTo(rule.threshold()) >= 0)
                        hits.add(hit(rule, List.of(current), "Transaction meets the USD reporting threshold of " + rule.threshold()));
                }
                case "STRUCTURING" -> {
                    List<Event> suspicious = window.stream().filter(e -> e.usdAmount().compareTo(rule.threshold()) >= 0
                        && e.usdAmount().compareTo(rule.secondaryThreshold()) < 0).toList();
                    if (suspicious.size() >= rule.minimumCount())
                        hits.add(hit(rule, suspicious, suspicious.size() + " transactions just below the USD reporting threshold within " + rule.windowHours() + " hours"));
                }
                case "RAPID_MOVEMENT" -> {
                    // For each deposit, only later outflows count. An earlier withdrawal cannot fund a later deposit.
                    for (Event deposit : window) {
                        if (!deposit.direction().equals("IN")) continue;
                        List<Event> outflows = window.stream().filter(e -> e.direction().equals("OUT") && e.time().isAfter(deposit.time())).toList();
                        if (!outflows.isEmpty() && sum(outflows).compareTo(deposit.baseAmount().multiply(rule.threshold())) >= 0) {
                            List<Event> evidence = new ArrayList<>(outflows); evidence.add(deposit);
                            hits.add(hit(rule, evidence, "Outflows after deposit " + deposit.id() + " exceed " + rule.threshold().multiply(BigDecimal.valueOf(100)) + "% of its INR value within " + rule.windowHours() + " hours"));
                        }
                    }
                }
                case "HIGH_RISK" -> {
                    if (watchlist.contains("JURISDICTION:" + current.jurisdiction())
                        || watchlist.contains("COUNTERPARTY:" + current.counterparty().strip().toUpperCase(Locale.ROOT)))
                        hits.add(hit(rule, List.of(current), "Jurisdiction or counterparty matches the configured high-risk watchlist"));
                }
                case "BEHAVIORAL" -> {
                    Instant day = current.time().atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
                    Instant start = day.minus(Duration.ofDays(90));
                    List<Event> baseline = history.stream().filter(e -> !e.time().isBefore(start) && e.time().isBefore(day)).toList();
                    List<Event> today = history.stream().filter(e -> !e.time().isBefore(day) && !e.time().isAfter(current.time())).toList();
                    // No alert for a customer with no observed history. Quiet days count in the 90-day denominator.
                    if (!baseline.isEmpty() && (sum(today).multiply(BigDecimal.valueOf(90)).compareTo(sum(baseline).multiply(rule.threshold())) > 0
                        || BigDecimal.valueOf(today.size() * 90L).compareTo(BigDecimal.valueOf(baseline.size()).multiply(rule.threshold())) > 0)) {
                        List<Event> evidence = new ArrayList<>(today); evidence.addAll(baseline);
                        hits.add(hit(rule, evidence, "UTC daily INR value or count exceeds " + rule.threshold() + " times the prior 90-calendar-day average; baseline count=" + baseline.size() + ", baseline INR=" + sum(baseline) + ", today count=" + today.size() + ", today INR=" + sum(today)));
                    }
                }
                case "ROUND_NUMBER" -> {
                    List<Event> round = window.stream().filter(e -> e.usdAmount().remainder(rule.threshold()).signum() == 0).toList();
                    if (round.size() >= rule.minimumCount())
                        hits.add(hit(rule, round, round.size() + " repeated USD-equivalent multiples of " + rule.threshold() + " within " + rule.windowHours() + " hours"));
                }
                default -> throw new IllegalArgumentException("Unknown detection rule: " + rule.code());
            }
        }
        return hits;
    }
    private Hit hit(Rule rule, List<Event> evidence, String reason) {
        return new Hit(rule, evidence.stream().map(Event::id).distinct().toList(), reason);
    }
    private BigDecimal sum(List<Event> events) {
        return events.stream().map(Event::baseAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
