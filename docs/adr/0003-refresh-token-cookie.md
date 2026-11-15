# 0003. Refresh token in an HttpOnly cookie, access token in memory

Status: accepted

## Context

The first version returned both tokens in JSON and the SPA kept the refresh token in `localStorage`. Any XSS
could then steal a 30-day credential. The API also has to serve non-browser clients.

## Decision

- The **access token** (15-minute JWT) is returned in the body and kept only in memory by the SPA.
- The **refresh token** (opaque, 30 days, rotated on every use, stored as a SHA-256 hash) is set as cookie
  `am_refresh`: `HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/api/v1/auth`. It is not included in the body.
- `/refresh` and `/logout` read the cookie and also require an `X-Requested-With` header.
- Native clients opt into body delivery with `X-Token-Delivery: body`.

## Consequences

- Script can't read the long-lived credential. XSS can still act while the page is open, but can't take the
  session away with it.
- `SameSite=Strict` plus the custom-header check rules out CSRF on the only endpoints that use the cookie.
  All other endpoints use the bearer header, which browsers never attach automatically.
- The narrow path means the cookie is only sent to the auth endpoints, not with every API call.
- The frontend and API must share a site (same origin, or same-site subdomains), which the nginx and Vite
  proxy setups provide.
- Reusing an already-rotated refresh token revokes every session of that user.

## Alternatives considered

- **Both tokens in `localStorage`.** Simple, but XSS steals everything.
- **Server-side sessions with a session cookie.** Fine for a single web client, but it means CSRF protection
  on every endpoint and sticky or shared sessions, and it doesn't suit native clients.
- **BFF (backend-for-frontend) holding tokens.** The strongest option, but another service to run.
