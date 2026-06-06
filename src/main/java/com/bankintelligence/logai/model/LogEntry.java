package com.bankintelligence.logai.model;

public record LogEntry(
        String time,
        String severity,
        String service,
        String event,
        String trace
) {
}
