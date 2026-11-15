# Operations

## Continuous integration

[`.github/workflows/ci.yml`](../.github/workflows/ci.yml) runs on every pull request and on pushes to `main`:

| Job | What it does |
|-----|--------------|
| Backend | `./mvnw verify`: unit tests, then integration tests against real PostgreSQL, ClamAV, S3Mock and Mailpit (Testcontainers). Uploads test reports on failure and the jar for the next job. |
| Frontend | `npm ci`, Prettier check, type check, Vitest, production build. |
| End-to-end | Starts the services with Docker Compose, runs the built jar, runs Playwright, then checks that the run produced business metrics on the Prometheus endpoint. Uploads the Playwright report and backend log on failure. |
| Docker | Builds both images, to catch Dockerfile breakage. |

Dependabot ([`.github/dependabot.yml`](../.github/dependabot.yml)) opens weekly update PRs for Maven, npm and
Actions, and monthly ones for base images.

## Monitoring

```bash
docker compose --profile observability up -d     # Prometheus :9091, Tempo, Grafana :3001
cd backend && TRACING_EXPORT_ENABLED=true ./mvnw spring-boot:run
```

Open http://localhost:3001: the **Advertising Marketplace** dashboard is the home page (no login locally). It
shows traffic, error ratio and p95 latency by endpoint, deal/offer/message/review activity, uploads and logins
by outcome, ClamAV and S3 latency, scheduled jobs, exchange-rate age, live streams, JVM and connection pool.
Points on the latency graphs are exemplars: click one to open that request's trace in Tempo. Alert rules
(`observability/prometheus/alerts.yml`) are visible at http://localhost:9091/alerts.

- **Ports.** Actuator (health details, `/actuator/prometheus`, `/actuator/metrics`) is on the **management
  port 8081**, which has no authentication and must never be exposed publicly. The public port only has
  `/livez` and `/readyz` for load balancers and orchestrators.
- **Tracing.** Every response carries `X-Trace-Id`, every error body has a `traceId`, and log lines include
  the trace and span ids. Spans include HTTP, security, scheduled jobs, the ClamAV scan, the S3 upload and the
  ECB fetch.
- **Business metrics** (`marketplace_*`): `auth_logins_total{outcome}`, `images_uploads_total{outcome}`,
  `images_scan_seconds`, `images_store_seconds`, `offers_sent_total`, `offers_decisions_total{status}`,
  `deals_events_total{event}`, `messages_sent_total{type}`, `reviews_submitted_total{rating}`,
  `social_syncs_total{provider,outcome}`, `exchange_rates_refreshes_total{outcome}`,
  `exchange_rates_age_seconds`, `realtime_sse_connections`.

The related environment variables are in [configuration.md](configuration.md#monitoring).

## Database migrations

The schema is managed by Flyway: migrations live in `backend/src/main/resources/db/migration` and run
automatically at startup. Hibernate runs with `ddl-auto: validate`, so it never changes the schema;
it only checks that the entities match it. To change the schema, add a new `V<n>__description.sql` file
and don't edit migrations that have already been applied.
