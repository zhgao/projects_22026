package com.bankintelligence.logai.model;

public record Incident(
        String title,
        String severity,
        int confidence,
        String owner,
        String impact,
        String cause,
        String next,
        String evidence
) {
}
