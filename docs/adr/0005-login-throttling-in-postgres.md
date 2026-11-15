# 0005. Login throttling in PostgreSQL, per email+IP and per email

Status: accepted

## Context

The first version counted failures per email in memory. That has two problems: each instance had its own
counter, and anyone could lock a real user out by failing logins for that user's email.

## Decision

Store failure counters in a `login_attempts` table under two keys, with one atomic upsert per failure:

- `client:<email>|<ip>`: 5 failures per 15 minutes. Stops one attacker without affecting the real owner,
  who logs in from a different IP.
- `account:<email>`: 20 failures per 15 minutes. Caps distributed guessing against one account from many IPs.

Failures are recorded in a `REQUIRES_NEW` transaction so the failed login's rollback doesn't erase them. A
success clears the client key. Expired rows are purged by a scheduled job. Behind a proxy, the client IP
comes from `X-Forwarded-For`.

## Consequences

- Limits hold across instances with no new infrastructure, at the cost of one small write per failed login.
- An attacker controlling many IPs can still push one account to its 20-failure limit. That's an accepted
  trade-off: it requires effort, and it only blocks password login for 15 minutes.
- Trusting `X-Forwarded-For` requires that the backend is only reachable through a proxy that sets it.

## Alternatives considered

- **Redis with TTL keys.** Natural fit and faster, but another system for one feature.
- **Bucket4j or similar.** Good for request-rate limiting, but the goal here is failure counting with
  lockout semantics.
- **CAPTCHA after N failures.** A good addition at the UI layer later; it doesn't replace server-side limits.
