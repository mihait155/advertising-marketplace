# Architecture decision records

Each record explains one decision: the context, what was decided, the consequences, and what else was considered.
New decisions get the next number; a reversed decision gets a new record that supersedes the old one.

| # | Decision |
|---|----------|
| 0001 | [Modular monolith instead of microservices](0001-modular-monolith.md) |
| 0002 | [PostgreSQL LISTEN/NOTIFY for real-time fan-out](0002-postgres-listen-notify.md) |
| 0003 | [Refresh token in an HttpOnly cookie, access token in memory](0003-refresh-token-cookie.md) |
| 0004 | [Scan uploads before storing them, and strip metadata](0004-scan-before-store.md) |
| 0005 | [Login throttling in PostgreSQL, per email+IP and per email](0005-login-throttling-in-postgres.md) |
| 0006 | [Double-blind reviews](0006-double-blind-reviews.md) |
| 0007 | [Store EUR-normalised pay for cross-currency filtering](0007-eur-normalised-pay.md) |
| 0008 | [Deals as their own aggregate, with auto-approval and no payments yet](0008-deals-and-auto-approval.md) |
| 0009 | [Micrometer + OpenTelemetry, Prometheus pull, separate management port](0009-observability.md) |
| 0010 | [Integration tests against real dependencies with Testcontainers](0010-testcontainers.md) |
