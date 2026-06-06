package com.bankintelligence.logai;

import com.bankintelligence.logai.ai.AnalysisWorkflow;
import com.bankintelligence.logai.model.AnalysisResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class BankLogAiServer {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int PORT = Integer.getInteger("port", 8080);

    private final AnalysisWorkflow workflow = new AnalysisWorkflow();
    private volatile AnalysisResult lastResult = workflow.analyze("");

    public static void main(String[] args) throws IOException {
        new BankLogAiServer().start();
    }

    private void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/api/analyze", this::handleAnalyze);
        server.createContext("/api/ask", this::handleAsk);
        server.createContext("/", this::handleStatic);
        server.start();
        System.out.printf("Bank Log AI running at http://localhost:%d%n", PORT);
    }

    private void handleAnalyze(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "POST required"));
            return;
        }

        JsonNode body = JSON.readTree(exchange.getRequestBody());
        String rawLogs = body.path("logs").asText("");
        lastResult = workflow.analyze(rawLogs);
        sendJson(exchange, 200, lastResult);
    }

    private void handleAsk(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "POST required"));
            return;
        }

        JsonNode body = JSON.readTree(exchange.getRequestBody());
        String question = body.path("question").asText("");
        String answer = workflow.answer(question, lastResult);
        sendJson(exchange, 200, Map.of("answer", answer));
    }

    private void handleStatic(HttpExchange exchange) throws IOException {
        String requestPath = exchange.getRequestURI().getPath();
        String fileName = "/".equals(requestPath) ? "index.html" : requestPath.substring(1);
        Path file = Path.of(fileName).normalize();

        if (!Files.exists(file) || Files.isDirectory(file) || file.startsWith("src")) {
            sendText(exchange, 404, "Not found", "text/plain");
            return;
        }

        String contentType = contentType(file);
        byte[] body = Files.readAllBytes(file);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private static void sendJson(HttpExchange exchange, int status, Object value) throws IOException {
        byte[] body = JSON.writeValueAsBytes(value);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private static void sendText(HttpExchange exchange, int status, String value, String contentType) throws IOException {
        byte[] body = value.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private static String contentType(Path file) {
        String name = file.getFileName().toString();
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".js")) return "application/javascript; charset=utf-8";
        return "application/octet-stream";
    }
}
