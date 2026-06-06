package com.bankintelligence.logai.ai;

import com.bankintelligence.logai.model.LogEntry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LogParser {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern KV = Pattern.compile("(\\w+)=([^\\s,]+)");
    private static final List<LogEntry> SAMPLE = List.of(
            new LogEntry("09:04", "warning", "payment-gateway", "P95 latency crossed 850ms for card authorization endpoint", "trc-1842"),
            new LogEntry("09:06", "critical", "payment-gateway", "Transaction authorization timeout rate reached 7.8%", "trc-1848"),
            new LogEntry("09:13", "critical", "mobile-auth", "MFA challenge failures spiked for iOS app version 8.4.2", "trc-2010"),
            new LogEntry("09:21", "info", "change-control", "Deployment CHG-6821 completed for payment risk scoring rules", "chg-6821"),
            new LogEntry("09:25", "critical", "fraud-screening", "Decision service exceeded timeout budget for 12 regions", "trc-2116"),
            new LogEntry("09:39", "critical", "payment-gateway", "Failed transaction count exceeded control threshold", "trc-2269")
    );

    List<LogEntry> parse(String raw) {
        if (raw == null || raw.isBlank()) return new ArrayList<>(SAMPLE);
        String trimmed = raw.trim();
        List<LogEntry> json = parseJson(trimmed);
        if (!json.isEmpty()) return json;
        List<LogEntry> csv = parseCsv(trimmed);
        if (!csv.isEmpty()) return csv;
        return parseText(trimmed);
    }

    private List<LogEntry> parseJson(String raw) {
        try {
            JsonNode root = JSON.readTree(raw);
            List<LogEntry> entries = new ArrayList<>();
            if (root.isObject()) {
                if (root.has("logs")) root = root.get("logs");
                else entries.add(fromJson(root));
            }
            if (root.isArray()) {
                for (JsonNode node : root) entries.add(fromJson(node));
            }
            entries.removeIf(entry -> entry.event().isBlank());
            return entries;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private LogEntry fromJson(JsonNode node) {
        return new LogEntry(
                firstText(node, "time", "timestamp", "@timestamp", "ts"),
                normalizeSeverity(firstText(node, "severity", "level", "status")),
                fallback(firstText(node, "service", "app", "application", "system", "component"), "unknown-service"),
                fallback(firstText(node, "event", "message", "msg", "error"), node.toString()),
                fallback(firstText(node, "trace", "traceId", "trace_id", "requestId", "request_id"), "n/a")
        );
    }

    private List<LogEntry> parseCsv(String raw) {
        String[] lines = raw.split("\\R");
        if (lines.length < 2 || !lines[0].contains(",")) return List.of();
        String[] headers = splitCsv(lines[0]);
        List<LogEntry> entries = new ArrayList<>();
        for (int lineIndex = 1; lineIndex < lines.length; lineIndex++) {
            String[] values = splitCsv(lines[lineIndex]);
            entries.add(new LogEntry(
                    csv(headers, values, "time", "timestamp", "ts"),
                    normalizeSeverity(csv(headers, values, "severity", "level", "status")),
                    fallback(csv(headers, values, "service", "app", "system", "component"), "unknown-service"),
                    fallback(csv(headers, values, "event", "message", "msg", "error"), lines[lineIndex]),
                    fallback(csv(headers, values, "trace", "traceId", "trace_id", "requestId"), "n/a")
            ));
        }
        entries.removeIf(entry -> entry.event().isBlank());
        return entries;
    }

    private List<LogEntry> parseText(String raw) {
        List<LogEntry> entries = new ArrayList<>();
        for (String line : raw.split("\\R")) {
            if (line.isBlank()) continue;
            String severity = detectSeverity(line);
            String service = detectKeyValue(line, "service", "app", "component", "system");
            String trace = detectKeyValue(line, "trace", "traceId", "requestId", "span");
            String time = detectTime(line);
            entries.add(new LogEntry(time, severity, fallback(service, "unknown-service"), line.trim(), fallback(trace, "n/a")));
        }
        return entries;
    }

    private static String firstText(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull()) return value.asText();
        }
        return "";
    }

    private static String[] splitCsv(String line) {
        return line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
    }

    private static String csv(String[] headers, String[] values, String... names) {
        for (String name : names) {
            for (int index = 0; index < headers.length && index < values.length; index++) {
                if (headers[index].trim().equalsIgnoreCase(name)) {
                    return values[index].trim().replaceAll("^\"|\"$", "");
                }
            }
        }
        return "";
    }

    private static String normalizeSeverity(String value) {
        String text = value == null ? "" : value.toLowerCase(Locale.ROOT);
        if (text.contains("crit") || text.contains("fatal") || text.contains("error")) return "critical";
        if (text.contains("warn")) return "warning";
        return "info";
    }

    private static String detectSeverity(String line) {
        String text = line.toLowerCase(Locale.ROOT);
        if (text.contains("critical") || text.contains("fatal") || text.contains("error") || text.contains("failed")) return "critical";
        if (text.contains("warn") || text.contains("timeout") || text.contains("latency") || text.contains("denied")) return "warning";
        return "info";
    }

    private static String detectKeyValue(String line, String... keys) {
        Matcher matcher = KV.matcher(line);
        while (matcher.find()) {
            for (String key : keys) {
                if (matcher.group(1).equalsIgnoreCase(key)) return matcher.group(2);
            }
        }
        return "";
    }

    private static String detectTime(String line) {
        Matcher matcher = Pattern.compile("(\\d{2}:\\d{2}:\\d{2}|\\d{2}:\\d{2}|\\d{4}-\\d{2}-\\d{2}T[^\\s]+)").matcher(line);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
