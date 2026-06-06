# Bank Log AI

Local Java MVP for AI-assisted bank log analysis and incident reporting.

## Tech

- Java 17+
- LangChain4j `1.15.1`
- LangGraph4j `1.8.17`
- Maven
- Browser UI served by Java `HttpServer`

## Run

```bash
mvn compile exec:java
```

Then open:

```text
http://localhost:8080
```

## Run In GitHub Codespaces

1. Open the repository on GitHub.
2. Select `Code` -> `Codespaces` -> `Create codespace on main`.
3. Wait for the dev container setup to finish.
4. Run:

```bash
mvn compile exec:java
```

Codespaces will forward port `8080` and open the Bank Log AI app in the browser.

## Log Input Formats

The importer accepts:

- JSON array or `{ "logs": [...] }`
- CSV with headers such as `time,severity,service,event,trace`
- Plain text logs with optional key-value pairs such as `service=payment-gateway trace=trc-123`

The current `LocalBankAnalystModel` implements LangChain4j's `ChatModel` locally so the app works without sending bank data outside the machine. Replace it later with an approved OpenAI, Azure OpenAI, Ollama, or on-prem model implementation.
