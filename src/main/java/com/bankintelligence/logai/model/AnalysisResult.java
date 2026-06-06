package com.bankintelligence.logai.model;

import java.util.List;

public record AnalysisResult(
        Summary summary,
        List<LogEntry> logs,
        List<Incident> incidents,
        String report,
        List<String> hypotheses
) {
}
