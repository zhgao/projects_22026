package com.bankintelligence.logai.model;

import java.util.List;

public record Summary(
        int totalLogs,
        int criticalAlerts,
        int warningAlerts,
        int impactedServices,
        int failedTransactions,
        int meanConfidence,
        int riskScore
) {
    public static Summary empty() {
        return new Summary(0, 0, 0, 0, 0, 0, 0);
    }

    public static Summary from(List<LogEntry> logs, List<Incident> incidents) {
        int critical = (int) logs.stream().filter(log -> log.severity().equals("critical")).count();
        int warning = (int) logs.stream().filter(log -> log.severity().equals("warning")).count();
        int services = (int) logs.stream().map(LogEntry::service).distinct().count();
        int failed = 400 + critical * 620 + warning * 180;
        int confidence = incidents.isEmpty()
                ? 64
                : (int) Math.round(incidents.stream().mapToInt(Incident::confidence).average().orElse(64));
        int risk = Math.min(99, 28 + critical * 13 + warning * 5 + services * 2);
        return new Summary(logs.size(), critical, warning, services, failed, confidence, risk);
    }
}
