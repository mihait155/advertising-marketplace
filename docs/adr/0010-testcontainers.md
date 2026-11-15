# 0010. Integration tests against real dependencies with Testcontainers

Status: accepted

## Context

Most of the risk is in places mocks can't see: SQL and migrations, `NOTIFY` delivery, transactions and
locking, `FOR UPDATE SKIP LOCKED`, the ClamAV protocol, S3 signing, SMTP, and Spring Security wiring.

## Decision

- Integration tests (`*IT`) run against real PostgreSQL 17, ClamAV, S3Mock and Mailpit containers, started
  once per Spring test context and shared by every test class that uses that context. External HTTP APIs (ECB, Twitch, YouTube) are replaced by
  small in-process fake servers that speak the real protocol.
- Unit tests (`*Test`) cover rules that don't need I/O and run in seconds.
- Playwright drives two real browsers against the real backend in CI.

## Consequences

- Tests exercise the same migrations, SQL dialect and protocols as production. No H2 surprises.
- The suite needs Docker and takes a few minutes. That's fine in CI and acceptable locally, where
  `./mvnw test` stays fast.
- Fakes for third-party OAuth can drift from the real APIs. The real Twitch and YouTube integrations still
  need a manual check with real credentials.

## Alternatives considered

- **H2 and mocks.** Fast, but misses dialect differences, `LISTEN/NOTIFY`, locking behaviour and real
  protocol errors.
- **A shared test environment.** Realistic, but slow, flaky and hard to reset.
