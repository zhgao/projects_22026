package com.bankintelligence.logai.ai;

import com.bankintelligence.logai.model.AnalysisResult;
import com.bankintelligence.logai.model.Incident;
import com.bankintelligence.logai.model.LogEntry;
import com.bankintelligence.logai.model.Summary;
import dev.langchain4j.model.chat.ChatModel;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

public final class AnalysisWorkflow {
    private static final String RAW_LOGS = "rawLogs";
    private static final String PARSED_LOGS = "parsedLogs";
    private static final String INCIDENTS = "incidents";
    private static final String SUMMARY = "summary";
    private static final String REPORT = "report";

    private final LogParser parser = new LogParser();
    private final IncidentDetector detector = new IncidentDetector();
    private final LocalBankAnalystModel analystModel = new LocalBankAnalystModel();
    private final ChatModel chatModel = analystModel;

    public AnalysisResult analyze(String rawLogs) {
        AtomicReference<AnalysisResult> result = new AtomicReference<>();
        Map<String, Channel<?>> schema = new LinkedHashMap<>();
        schema.put(RAW_LOGS, Channels.base(() -> ""));
        schema.put(PARSED_LOGS, Channels.base(ArrayList::new));
        schema.put(INCIDENTS, Channels.base(ArrayList::new));
        schema.put(SUMMARY, Channels.base(() -> Summary.empty()));
        schema.put(REPORT, Channels.base(() -> ""));

        try {
            StateGraph<AgentState> graph = new StateGraph<>(schema, AgentState::new)
                    .addNode("parse_logs", parseNode())
                    .addNode("detect_incidents", detectNode())
                    .addNode("draft_report", reportNode(result))
                    .addEdge(START, "parse_logs")
                    .addEdge("parse_logs", "detect_incidents")
                    .addEdge("detect_incidents", "draft_report")
                    .addEdge("draft_report", END);

            CompiledGraph<AgentState> compiled = graph.compile();
            for (Object ignored : compiled.stream(Map.of(RAW_LOGS, rawLogs == null ? "" : rawLogs))) {
                // Iterating the stream executes each LangGraph4j node.
            }
            return result.get() == null ? fallback(rawLogs) : result.get();
        } catch (Exception exception) {
            return fallback(rawLogs);
        }
    }

    public String answer(String question, AnalysisResult result) {
        return chatModel.chat("""
                You are a private bank log analyst.
                Question: %s
                Current report: %s
                """.formatted(question, result.report()));
    }

    private AsyncNodeAction<AgentState> parseNode() {
        return state -> CompletableFuture.supplyAsync(() -> {
            String rawLogs = state.value(RAW_LOGS).map(String.class::cast).orElse("");
            List<LogEntry> parsed = parser.parse(rawLogs);
            return Map.of(PARSED_LOGS, parsed);
        });
    }

    private AsyncNodeAction<AgentState> detectNode() {
        return state -> CompletableFuture.supplyAsync(() -> {
            List<LogEntry> logs = state.value(PARSED_LOGS).map(value -> (List<LogEntry>) value).orElseGet(List::of);
            List<Incident> incidents = detector.detect(logs);
            Summary summary = Summary.from(logs, incidents);
            return Map.of(INCIDENTS, incidents, SUMMARY, summary);
        });
    }

    private AsyncNodeAction<AgentState> reportNode(AtomicReference<AnalysisResult> result) {
        return state -> CompletableFuture.supplyAsync(() -> {
            List<LogEntry> logs = state.value(PARSED_LOGS).map(value -> (List<LogEntry>) value).orElseGet(List::of);
            List<Incident> incidents = state.value(INCIDENTS).map(value -> (List<Incident>) value).orElseGet(List::of);
            Summary summary = state.value(SUMMARY).map(Summary.class::cast).orElseGet(Summary::empty);
            String report = analystModel.draftReport(summary, incidents);
            AnalysisResult analysis = new AnalysisResult(summary, logs, incidents, report, topHypotheses(incidents));
            result.set(analysis);
            return Map.of(REPORT, report);
        });
    }

    private AnalysisResult fallback(String rawLogs) {
        List<LogEntry> parsed = parser.parse(rawLogs == null ? "" : rawLogs);
        List<Incident> incidents = detector.detect(parsed);
        Summary summary = Summary.from(parsed, incidents);
        return new AnalysisResult(summary, parsed, incidents, analystModel.draftReport(summary, incidents), topHypotheses(incidents));
    }

    private static List<String> topHypotheses(List<Incident> incidents) {
        return incidents.stream()
                .sorted(Comparator.comparingInt(Incident::confidence).reversed())
                .limit(3)
                .map(incident -> incident.cause() + " Evidence: " + incident.evidence())
                .toList();
    }
}
