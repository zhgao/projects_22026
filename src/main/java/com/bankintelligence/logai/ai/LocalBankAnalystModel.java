package com.bankintelligence.logai.ai;

import com.bankintelligence.logai.model.Incident;
import com.bankintelligence.logai.model.Summary;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.List;
import java.util.Locale;

final class LocalBankAnalystModel implements ChatModel {
    @Override
    public ChatResponse chat(ChatRequest chatRequest) {
        String text = chatRequest.messages().stream()
                .map(LocalBankAnalystModel::messageText)
                .reduce("", (left, right) -> left + "\n" + right);
        return ChatResponse.builder()
                .aiMessage(AiMessage.from(chat(text)))
                .build();
    }

    String draftReport(Summary summary, List<Incident> incidents) {
        Incident top = incidents.isEmpty() ? null : incidents.get(0);
        StringBuilder report = new StringBuilder();
        report.append("Executive Summary\n");
        report.append("AI workflow analyzed ").append(summary.totalLogs()).append(" log entries across ")
                .append(summary.impactedServices()).append(" services. Risk score is ")
                .append(summary.riskScore()).append(" with ").append(summary.meanConfidence()).append("% mean confidence.\n\n");

        report.append("Business Impact\n");
        if (top == null) {
            report.append("No material customer or operational impact was detected in this window.\n\n");
        } else {
            report.append(top.impact()).append("\n\n");
        }

        report.append("Likely Root Cause\n");
        report.append(top == null ? "No correlated root-cause pattern found." : top.cause()).append("\n\n");

        report.append("Evidence\n");
        incidents.stream().limit(4).forEach(incident ->
                report.append("- ").append(incident.title()).append(": ").append(incident.evidence()).append("\n"));

        report.append("\nRecommended Actions\n");
        incidents.stream().limit(4).forEach(incident ->
                report.append("- ").append(incident.next()).append("\n"));
        return report.toString();
    }

    @Override
    public String chat(String userMessage) {
        String question = userMessage.toLowerCase(Locale.ROOT);
        if (question.contains("root") || question.contains("cause")) {
            return "The strongest root-cause answer is in the top incident cause and supporting evidence. Check the change, timeout, and saturation signals before treating symptoms.";
        }
        if (question.contains("impact") || question.contains("customer")) {
            return "Impact should be read from the top incident: transaction, login, or service degradation estimates are generated from critical/warning event density.";
        }
        if (question.contains("next") || question.contains("action") || question.contains("fix")) {
            return "Next action: preserve evidence, assign the service owner, validate the latest change window, and apply rollback or isolation only after approval.";
        }
        return "I can answer from the current analysis result. Ask about root cause, customer impact, evidence, or next action.";
    }

    private static String messageText(ChatMessage message) {
        if (message instanceof UserMessage userMessage && userMessage.hasSingleText()) {
            return userMessage.singleText();
        }
        if (message instanceof SystemMessage systemMessage) {
            return systemMessage.text();
        }
        if (message instanceof AiMessage aiMessage) {
            return aiMessage.text();
        }
        return message.toString();
    }
}
