const sampleLogs = [
  ["09:04", "warning", "payment-gateway", "P95 latency crossed 850ms for card authorization endpoint", "trc-1842"],
  ["09:06", "critical", "payment-gateway", "Transaction authorization timeout rate reached 7.8%", "trc-1848"],
  ["09:13", "critical", "mobile-auth", "MFA challenge failures spiked for iOS app version 8.4.2", "trc-2010"],
  ["09:21", "info", "change-control", "Deployment CHG-6821 completed for payment risk scoring rules", "chg-6821"],
  ["09:25", "critical", "fraud-screening", "Decision service exceeded timeout budget for 12 regions", "trc-2116"],
  ["09:39", "critical", "payment-gateway", "Failed transaction count exceeded control threshold", "trc-2269"]
];

let logs = [];
let incidents = [];
let report = "";
let hypotheses = [];
let summary = {};
let selectedSeverity = "all";

const $ = (selector) => document.querySelector(selector);
const $$ = (selector) => [...document.querySelectorAll(selector)];

function sampleLogText() {
  return sampleLogs
    .map(([time, severity, service, event, trace]) => `${time} level=${severity} service=${service} trace=${trace} ${event}`)
    .join("\n");
}

function services() {
  return [...new Set(logs.map((log) => log.service))].sort();
}

function currentLogs() {
  const service = $("#serviceFilter").value;
  const query = $("#searchBox")?.value.toLowerCase() || "";
  return logs.filter((log) => {
    const serviceMatch = service === "all" || service === log.service;
    const severityMatch = selectedSeverity === "all" || selectedSeverity === log.severity;
    const queryMatch = [log.time, log.severity, log.service, log.event, log.trace].join(" ").toLowerCase().includes(query);
    return serviceMatch && severityMatch && queryMatch;
  });
}

function severityBadge(severity) {
  return `<span class="badge ${severity}">${severity}</span>`;
}

async function postJson(url, payload) {
  const response = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
  if (!response.ok) throw new Error(`Request failed: ${response.status}`);
  return response.json();
}

async function analyze(rawText = "") {
  const result = await postJson("/api/analyze", { logs: rawText });
  logs = result.logs || [];
  incidents = result.incidents || [];
  report = result.report || "";
  hypotheses = result.hypotheses || [];
  summary = result.summary || {};
  refresh();
}

function renderServiceFilter() {
  const previous = $("#serviceFilter").value;
  $("#serviceFilter").innerHTML = `<option value="all">All services</option>`;
  services().forEach((service) => {
    const option = document.createElement("option");
    option.value = service;
    option.textContent = service;
    $("#serviceFilter").appendChild(option);
  });
  if (services().includes(previous)) $("#serviceFilter").value = previous;
}

function renderMetrics() {
  $("#criticalCount").textContent = summary.criticalAlerts || 0;
  $("#serviceCount").textContent = summary.impactedServices || 0;
  $("#failureCount").textContent = (summary.failedTransactions || 0).toLocaleString();
  $("#confidenceScore").textContent = `${summary.meanConfidence || 0}%`;
  $("#riskScore").textContent = summary.riskScore || 0;

  const topIncident = incidents[0];
  $("#summaryTitle").textContent = topIncident ? topIncident.title : "No incident detected";
  $("#summaryText").textContent = topIncident
    ? `${topIncident.impact} Most likely cause: ${topIncident.cause}`
    : "Import logs or run analysis to generate incident hypotheses.";
}

function renderChart() {
  const buckets = new Map();
  logs.forEach((log) => {
    const label = (log.time || "n/a").slice(0, 5) || "n/a";
    const bucket = buckets.get(label) || { count: 0, severity: "info" };
    bucket.count += log.severity === "critical" ? 3 : log.severity === "warning" ? 2 : 1;
    if (log.severity === "critical") bucket.severity = "critical";
    else if (log.severity === "warning" && bucket.severity !== "critical") bucket.severity = "warning";
    buckets.set(label, bucket);
  });

  const entries = [...buckets.entries()].slice(-12);
  const max = Math.max(1, ...entries.map(([, bucket]) => bucket.count));
  $("#chart").innerHTML = entries
    .map(([label, bucket]) => {
      const height = Math.max(18, Math.round((bucket.count / max) * 92));
      return `<div class="bar ${bucket.severity}" style="height:${height}%"><span>${label}</span></div>`;
    })
    .join("");
}

function renderHypotheses() {
  $("#hypotheses").innerHTML = hypotheses.length
    ? hypotheses.map((item) => `<li>${escapeHtml(item)}</li>`).join("")
    : "<li>No high-confidence hypothesis has been generated yet.</li>";
}

function renderLogs() {
  $("#logRows").innerHTML = currentLogs()
    .map(
      (log) => `
        <tr>
          <td>${escapeHtml(log.time || "")}</td>
          <td>${severityBadge(log.severity || "info")}</td>
          <td>${escapeHtml(log.service || "")}</td>
          <td>${escapeHtml(log.event || "")}</td>
          <td>${escapeHtml(log.trace || "")}</td>
        </tr>
      `
    )
    .join("");
}

function renderIncidents() {
  $("#incidentList").innerHTML = incidents
    .map(
      (incident) => `
        <article class="incident ${incident.severity}">
          <div>
            <h3>${escapeHtml(incident.title)}</h3>
            <p>${escapeHtml(incident.impact)}</p>
            <div class="incident-meta">
              <span>${escapeHtml(incident.owner)}</span>
              <span>${incident.confidence}% confidence</span>
              <span>${escapeHtml(incident.severity)}</span>
            </div>
          </div>
          <button data-report="${escapeHtml(incident.title)}">Draft report</button>
        </article>
      `
    )
    .join("");
}

function renderReport() {
  $("#reportTime").textContent = new Date().toLocaleString();
  $("#reportBody").innerHTML = report
    .split(/\n{2,}/)
    .map((block) => {
      const lines = block.split("\n");
      if (lines.length === 1) return `<p>${escapeHtml(block)}</p>`;
      const [heading, ...rest] = lines;
      const listItems = rest.filter((line) => line.startsWith("- ")).map((line) => `<li>${escapeHtml(line.slice(2))}</li>`);
      const paragraph = rest.filter((line) => !line.startsWith("- ")).map(escapeHtml).join("<br />");
      return `<h4>${escapeHtml(heading)}</h4>${paragraph ? `<p>${paragraph}</p>` : ""}${listItems.length ? `<ul>${listItems.join("")}</ul>` : ""}`;
    })
    .join("");
}

function renderChat() {
  $("#chat").innerHTML = `
    <div class="message">The backend uses LangChain4j's ChatModel interface. Ask about root cause, customer impact, evidence, or next action.</div>
  `;
}

function refresh() {
  renderServiceFilter();
  renderMetrics();
  renderChart();
  renderHypotheses();
  renderLogs();
  renderIncidents();
  renderReport();
}

function bindEvents() {
  $$(".nav button").forEach((button) => {
    button.addEventListener("click", () => {
      $$(".nav button").forEach((item) => item.classList.remove("active"));
      $$(".view").forEach((view) => view.classList.remove("active"));
      button.classList.add("active");
      $(`#${button.dataset.view}`).classList.add("active");
    });
  });

  $$(".segmented button").forEach((button) => {
    button.addEventListener("click", () => {
      $$(".segmented button").forEach((item) => item.classList.remove("active"));
      button.classList.add("active");
      selectedSeverity = button.dataset.severity;
      renderLogs();
    });
  });

  $("#serviceFilter").addEventListener("change", renderLogs);
  $("#sensitivity").addEventListener("input", refresh);
  $("#searchBox").addEventListener("input", renderLogs);
  $("#runAnalysis").addEventListener("click", () => analyze($("#rawLogs").value));
  $("#analyzeRaw").addEventListener("click", () => analyze($("#rawLogs").value));
  $("#logFile").addEventListener("change", async (event) => {
    const file = event.target.files[0];
    if (!file) return;
    const text = await file.text();
    $("#rawLogs").value = text;
    await analyze(text);
  });

  $("#exportReport").addEventListener("click", () => {
    const blob = new Blob([report], { type: "text/plain" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = "bank-ai-incident-report.txt";
    link.click();
    URL.revokeObjectURL(url);
  });

  $("#incidentList").addEventListener("click", (event) => {
    if (event.target.matches("[data-report]")) {
      $$(".nav button").find((button) => button.dataset.view === "reports").click();
      renderReport();
    }
  });

  $("#askForm").addEventListener("submit", async (event) => {
    event.preventDefault();
    const input = $("#askInput");
    const question = input.value.trim();
    if (!question) return;
    $("#chat").insertAdjacentHTML("beforeend", `<div class="message user">${escapeHtml(question)}</div>`);
    input.value = "";
    try {
      const result = await postJson("/api/ask", { question });
      $("#chat").insertAdjacentHTML("beforeend", `<div class="message">${escapeHtml(result.answer)}</div>`);
    } catch (error) {
      $("#chat").insertAdjacentHTML("beforeend", `<div class="message">The Java backend is not reachable.</div>`);
    }
  });
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

$("#rawLogs").value = sampleLogText();
renderChat();
bindEvents();
analyze($("#rawLogs").value).catch(() => {
  logs = sampleLogs.map(([time, severity, service, event, trace]) => ({ time, severity, service, event, trace }));
  incidents = [];
  report = "Java backend is not reachable. Start it with mvn compile exec:java.";
  hypotheses = ["Start the Java backend to run the LangGraph4j workflow."];
  summary = { totalLogs: logs.length, criticalAlerts: 0, warningAlerts: 0, impactedServices: 0, failedTransactions: 0, meanConfidence: 0, riskScore: 0 };
  refresh();
});
