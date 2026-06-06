# AI-Assisted Log Analysis System Summary

## Overview

The AI-assisted log analysis system is a local Java MVP for bank operations teams to import logs, detect incident patterns, generate incident summaries, and ask follow-up questions about the current analysis. It is designed as a private, offline-first prototype: the included analyst model runs locally and does not send bank log data to an external service.

The project is implemented in `bank-log-ai` and is served as a browser-based command center backed by a Java HTTP server.

## Primary Goals

- Parse operational, application, and security log exports from common formats.
- Detect likely service incidents using banking-oriented heuristics.
- Estimate operational impact, risk, confidence, and affected services.
- Generate an executive-style incident report with evidence and recommended actions.
- Provide a simple analyst Q&A interface for root cause, impact, evidence, and next-step questions.
- Keep sensitive bank data local by default.

## Technology Stack

- Java 17+
- Maven
- LangChain4j `1.15.1`
- LangGraph4j `1.8.17`
- Jackson Databind `2.20.1`
- Java built-in `HttpServer`
- Browser UI with plain HTML, CSS, and JavaScript

## Runtime Flow

The main backend entry point is `BankLogAiServer`. When started, it serves the UI and exposes two API endpoints:

- `POST /api/analyze`: accepts raw logs and returns the full analysis result.
- `POST /api/ask`: answers a question using the latest analysis result.

The analysis itself is handled by `AnalysisWorkflow`, which builds a LangGraph4j workflow with three nodes:

1. `parse_logs`: converts raw text into normalized `LogEntry` records.
2. `detect_incidents`: groups logs by service, detects incidents, and builds summary metrics.
3. `draft_report`: generates a report and top hypotheses from the detected incidents.

If the LangGraph4j workflow fails, the system falls back to the same parse, detect, and report logic directly, so analysis can still complete.

## Log Input Support

The importer accepts:

- JSON arrays.
- JSON objects with a `logs` array.
- Single JSON log objects.
- CSV files with headers such as `time`, `timestamp`, `severity`, `level`, `service`, `message`, `event`, and `trace`.
- Plain text logs with optional key-value fields such as `service=payment-gateway` and `trace=trc-123`.

Parsed logs are normalized into:

- `time`
- `severity`
- `service`
- `event`
- `trace`

If no logs are provided, the system uses a built-in sample banking incident window involving payment latency, authorization failures, MFA failures, change activity, and fraud-screening timeouts.

## Incident Detection Logic

`IncidentDetector` performs deterministic local analysis rather than calling a remote model. It groups logs by service and looks for severity and keyword patterns such as:

- `timeout`
- `failed`
- `denied`
- `latency`
- `saturation`
- `fraud`
- `mfa`
- `unauthorized`
- `error`

For each service that crosses a local risk threshold, it creates an `Incident` containing:

- Incident title.
- Severity.
- Confidence score.
- Owning team.
- Estimated business impact.
- Likely root cause.
- Recommended next action.
- Supporting evidence from the logs.

Incidents are sorted by confidence so the most likely or urgent issue appears first.

## Summary Metrics

The system calculates a `Summary` object containing:

- Total parsed logs.
- Critical alert count.
- Warning alert count.
- Number of impacted services.
- Estimated failed transactions.
- Mean incident confidence.
- Risk score.

The risk score is a simple capped formula based on critical alerts, warning alerts, and impacted services. The failed transaction estimate is also heuristic and intended for MVP triage, not audited reporting.

## Analyst Model

`LocalBankAnalystModel` implements LangChain4j's `ChatModel` interface locally. It has two roles:

- Draft the incident report from summary metrics and detected incidents.
- Answer simple analyst questions about root cause, customer impact, evidence, and next action.

The current model is rule-based and deterministic. It is shaped like a LangChain4j model adapter so it can later be replaced with an approved OpenAI, Azure OpenAI, Ollama, or on-prem model implementation without changing the rest of the workflow design.

## User Interface

The browser UI is a command-center style application with four main views:

- `Overview`: risk score, critical alert count, impacted services, failed transaction estimate, mean confidence, event chart, and top hypotheses.
- `Log Stream`: raw log import, search, service filter, severity filter, and normalized log table.
- `Incidents`: detected incidents with owner, severity, confidence, and impact.
- `Reports`: generated incident report, report export, and private analyst Q&A.

Users can paste logs, upload a log file, run analysis, inspect incidents, filter the log stream, ask follow-up questions, and export the generated report.

## Privacy And Deployment Posture

The MVP is intentionally local-first. The included model does not transmit logs outside the machine. This is important for bank data, where logs may contain sensitive operational, customer, transaction, or security details.

The README notes that the local model can be replaced later with an approved model provider or on-prem model. Any such replacement should include security review, data classification review, retention controls, and audit logging before production use.

## Current Limitations

- Incident detection is heuristic, not statistically trained or model-driven.
- Risk score, failed transaction estimates, and confidence scores are approximations.
- The UI sensitivity slider exists but does not currently change backend detection thresholds.
- The analyst Q&A answers broad categories of questions rather than performing deep retrieval over every log line.
- The static file handler is suitable for a local MVP, not hardened production hosting.
- There is no authentication, authorization, audit trail, persistence, or multi-user session isolation.
- There are no automated tests in the current project snapshot.

## How To Run

From the `bank-log-ai` directory:

```bash
mvn compile exec:java
```

Then open:

```text
http://localhost:8080
```

## Overall Assessment

This system is a focused MVP for demonstrating AI-assisted bank log triage. Its strongest design choices are the local privacy-preserving workflow, normalized multi-format log ingestion, clear incident data model, and replaceable LangChain4j-compatible analyst interface.

The system is best understood as a prototype command center for incident analysis, not a production observability platform. To move toward production, the next priorities would be hardened security, real model integration or calibrated detection, persistent storage, auditability, tests, and stronger integration with existing bank observability and incident-management systems.
