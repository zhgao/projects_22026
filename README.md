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

Use Case 2 — Codebase Understanding, Reverse Engineering, and Living
Documentation Engine
Problem Statement
Missing documentation and hard-to-understand legacy systems are often two symptoms of the same
problem. In many critical applications, the real design exists only in source code, configuration,
commit history, and the heads of a few experienced engineers. This makes onboarding slow, change
risk high, and cross-team collaboration expensive. What enterprises need is not only a document
generator, but an engine that can reverse-engineer structure, business rules, dependencies, and
operational knowledge from the existing codebase and keep that understanding current.
The stronger use case is therefore a unified capability that combines documentation generation,
knowledge retrieval, legacy code navigation, and design reconstruction. AIFOD or a related AIassisted
coding assistant helps teams understand why the system was designed in a certain way,
which modules are risky, which dependencies are aging, and how to turn that insight into living
documentation.
Challenge
The system of record is fragmented: Code, configuration, deployment scripts, PR descriptions,
and internal wikis often disagree.
Understanding code requires more than reading syntax: The solution must infer design intent,
historical tradeoffs, business rules, and failure modes.
Documentation goes stale quickly: A one-time generated document loses value unless it
evolves with the implementation.
Legacy systems often lack tests and clean boundaries: Reverse engineering and
modernization suggestions cannot rely on static reading alone.
Different audiences need different outputs: Developers, architects, operators, auditors, and
new team members need different levels of abstraction.
Benefits
Accelerates onboarding significantly: New engineers get fast explanations of system structure,
key modules, and risk hotspots.
Turns tacit knowledge into explicit assets: Architecture maps, dependency views, operational
notes, and business rule summaries can be preserved.
Supports safer legacy modernization: Teams can make more reliable decomposition,
refactoring, and migration decisions.
Creates living documentation: Documentation can be regenerated from code and delivery
artifacts instead of remaining a stale snapshot.
Reduces cross-team communication overhead: Engineering, QA, operations, and management
can work from the same knowledge base.
