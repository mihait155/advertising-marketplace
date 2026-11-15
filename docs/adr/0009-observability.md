# 0009. Micrometer + OpenTelemetry, Prometheus pull, separate management port

Status: accepted

## Context

Questions an operator needs to answer: Is it up? Is it slow, and where? Are uploads failing because of
ClamAV? Are people completing deals? Did the nightly sync work? Is someone stuffing credentials? Support also
needs to go from "I got an error" to the exact request.

## Decision

- **Metrics:** Micrometer with the Prometheus registry, scraped by Prometheus. Business counters live in one
  class, `MarketplaceMetrics`, so names and tags are consistent and reviewable. Counters for transactional
  events increment after commit.
- **Traces:** Micrometer Observation bridged to OpenTelemetry, exported over OTLP (Tempo locally; any OTLP
  backend in production). Expensive dependencies (ClamAV, S3, ECB) get their own spans. Latency histograms
  carry exemplars linking to traces.
- **Correlation:** an `X-Trace-Id` response header, a `traceId` in every problem-detail body, and trace ids
  in log lines.
- **Actuator on a separate management port (8081)** that is never published, with its own security filter
  chain. Only `/livez` and `/readyz` are on the public port.
- **Dashboard and alert rules as code** in `observability/`, provisioned into Grafana and Prometheus.

## Consequences

- Vendor-neutral: switching to Datadog, Honeycomb or Grafana Cloud is configuration, not code.
- A pull model means the app doesn't need to know where metrics go, but Prometheus must be able to reach
  every instance (service discovery in a real deployment).
- Tracing at 100% sampling is the local default; production should lower `TRACING_SAMPLING_PROBABILITY`.
- Keeping actuator off the public port avoids leaking internals (env, metrics, heap info) without relying on
  per-endpoint security rules.
- Gotcha found on the way: Prometheus' client strips a trailing `_created` from counter names (it's a
  reserved OpenMetrics suffix), so the offers counter is named `marketplace.offers.sent`.

## Alternatives considered

- **Logs only.** Cheap, but answers none of the latency or trend questions.
- **A vendor agent (APM).** Less setup, but it adds lock-in and hides what is measured.
- **Actuator on the main port with auth.** Works, but one misconfigured rule exposes it to the internet.
