package com.bankintelligence.logai.ai;

import com.bankintelligence.logai.model.Incident;
import com.bankintelligence.logai.model.LogEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class IncidentDetector {
    List<Incident> detect(List<LogEntry> logs) {
        Map<String, List<LogEntry>> byService = new LinkedHashMap<>();
        for (LogEntry log : logs) {
            byService.computeIfAbsent(log.service(), ignored -> new ArrayList<>()).add(log);
        }

        List<Incident> incidents = new ArrayList<>();
        for (Map.Entry<String, List<LogEntry>> entry : byService.entrySet()) {
            String service = entry.getKey();
            List<LogEntry> serviceLogs = entry.getValue();
            long critical = serviceLogs.stream().filter(log -> log.severity().equals("critical")).count();
            long warning = serviceLogs.stream().filter(log -> log.severity().equals("warning")).count();
            String corpus = serviceLogs.stream().map(LogEntry::event).reduce("", (left, right) -> left + " " + right).toLowerCase(Locale.ROOT);

            if (critical == 0 && warning < 2 && !looksRisky(corpus)) {
                continue;
            }

            String title = titleFor(service, corpus);
            String severity = critical > 0 || looksSecurityCritical(corpus) ? "critical" : "warning";
            int confidence = Math.min(96, 62 + (int) critical * 10 + (int) warning * 5 + keywordBoost(corpus));
            String impact = impactFor(service, corpus, critical, warning);
            String cause = causeFor(service, corpus);
            String evidence = serviceLogs.stream()
                    .limit(4)
                    .map(log -> log.time() + " " + log.trace() + " " + log.event())
                    .reduce((left, right) -> left + "; " + right)
                    .orElse("No evidence available.");
            String next = nextActionFor(service, corpus);
            incidents.add(new Incident(title, severity, confidence, ownerFor(service), impact, cause, next, evidence));
        }

        if (incidents.isEmpty() && !logs.isEmpty()) {
            incidents.add(new Incident(
                    "No critical incident detected",
                    "info",
                    71,
                    "Operations",
                    "Logs were parsed successfully and no high-risk pattern crossed the local threshold.",
                    "No correlated failure pattern found in the current sample.",
                    "Continue monitoring and import a larger window if this was a partial export.",
                    "Parsed " + logs.size() + " log entries."
            ));
        }

        incidents.sort(Comparator.comparingInt(Incident::confidence).reversed());
        return incidents;
    }

    private static boolean looksRisky(String text) {
        return text.contains("timeout") || text.contains("failed") || text.contains("denied")
                || text.contains("latency") || text.contains("saturation") || text.contains("fraud")
                || text.contains("mfa") || text.contains("unauthorized") || text.contains("error");
    }

    private static boolean looksSecurityCritical(String text) {
        return text.contains("unauthorized") || text.contains("privilege") || text.contains("mfa")
                || text.contains("failed login") || text.contains("credential");
    }

    private static int keywordBoost(String text) {
        int boost = 0;
        for (String keyword : List.of("timeout", "failed", "fraud", "mfa", "authorization", "saturation", "502", "error")) {
            if (text.contains(keyword)) boost += 3;
        }
        return boost;
    }

    private static String titleFor(String service, String text) {
        if (text.contains("authorization") || service.contains("payment")) return "Payment authorization degradation";
        if (text.contains("mfa") || service.contains("auth")) return "Authentication failure spike";
        if (text.contains("fraud")) return "Fraud decision service instability";
        if (text.contains("ledger")) return "Ledger processing pressure";
        return service + " operational anomaly";
    }

    private static String impactFor(String service, String text, long critical, long warning) {
        int estimated = 400 + (int) critical * 620 + (int) warning * 180;
        if (text.contains("authorization") || service.contains("payment")) {
            return "Estimated " + estimated + " failed or delayed transaction attempts in the imported window.";
        }
        if (text.contains("mfa") || service.contains("auth")) {
            return "Customer login completion may be degraded for affected digital banking users.";
        }
        return "Operational impact detected for " + service + " with " + critical + " critical and " + warning + " warning events.";
    }

    private static String causeFor(String service, String text) {
        if (text.contains("deployment") || text.contains("chg-")) return "Recent change activity is correlated with downstream errors.";
        if (text.contains("timeout")) return "Timeout growth suggests a dependency or capacity bottleneck.";
        if (text.contains("saturation") || text.contains("pool")) return "Resource saturation is likely amplifying request retries.";
        if (text.contains("mfa")) return "MFA challenge failures indicate an identity workflow or client-version issue.";
        return "The service shows abnormal severity concentration compared with the rest of the log window.";
    }

    private static String nextActionFor(String service, String text) {
        if (text.contains("deployment") || text.contains("chg-")) return "Review the change record and prepare rollback or feature-flag isolation.";
        if (text.contains("timeout")) return "Check dependency latency, queue depth, and recent release activity before scaling.";
        if (text.contains("mfa")) return "Segment failures by device, app version, and challenge provider.";
        return "Assign service owner, preserve traces, and compare against the previous healthy window.";
    }

    private static String ownerFor(String service) {
        if (service.contains("payment")) return "Payments Platform";
        if (service.contains("auth")) return "Digital Identity";
        if (service.contains("fraud")) return "Fraud Engineering";
        if (service.contains("ledger")) return "Core Banking";
        return "Operations";
    }
}
