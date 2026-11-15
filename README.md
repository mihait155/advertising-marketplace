# Advertising Marketplace

[![CI](https://github.com/mihait155/marketing-marketplace/actions/workflows/ci.yml/badge.svg)](https://github.com/mihait155/marketing-marketplace/actions/workflows/ci.yml)

A place where brands find creators to advertise with, and creators find paid work.

Streamers, YouTubers and other people with an audience post what they offer. Advertisers post campaigns
they need help with. Either side can respond to the other with an offer, talk it through in a chat, and
once an offer is accepted it turns into a deal: the creator delivers, the advertiser approves, and both
leave a review.

## What it does

- Postings for campaigns and services, with Markdown, images and a pay range in EUR, USD, GBP or RON.
  You can filter by pay in any of those currencies.
- Offers with a price range, revisable until the other side accepts or declines.
- Real-time chat per posting, with read receipts, typing indicators, photos, blocking and reporting.
- Deals that track delivery: the creator submits links, the advertiser approves or asks for changes, and
  it approves itself after 7 days if nobody answers.
- Reviews that stay hidden until both sides have written theirs, so nobody can retaliate.
- Verified audience numbers pulled from Twitch and YouTube instead of typed in by hand.
- Every uploaded image is virus-scanned and stripped of GPS and other metadata before it's stored.

## Stack

Java 21 and Spring Boot 4 on the backend, PostgreSQL for data (and for pushing chat events between
instances), S3 for images, ClamAV for scanning. The frontend is React 19 with TypeScript, TanStack Query
and plain CSS Modules. Tests use JUnit, Testcontainers, Vitest and Playwright. Prometheus, Tempo and
Grafana are set up for monitoring.

## Running it locally

You need Docker, Java 21 and Node 22.

```bash
docker compose up -d postgres s3mock clamav mailpit

# API on http://localhost:8080
cd backend
ADMIN_EMAIL=admin@example.com ADMIN_PASSWORD='Change-me-123' ./mvnw spring-boot:run

# in another terminal: app on http://localhost:5173
cd frontend
npm ci
node scripts/seed-demo.mjs   # optional demo data
npm run dev
```

The seed script creates a few accounts you can log in with, all with the password `Demo-passw0rd`, for
example `maya@creators.test` (a creator) or `marketing@northwind.test` (an advertiser). Emails the app
sends end up in Mailpit at http://localhost:8025.

If you'd rather not install Java or Node, `docker compose --profile app up --build` runs everything and
serves the app on http://localhost:3000.

## Tests

```bash
cd backend && ./mvnw verify     # unit + integration tests, needs Docker
cd frontend && npm test         # unit tests
cd frontend && npm run test:e2e # Playwright, against a running backend
```

CI runs all three on every pull request. See [docs/operations.md](docs/operations.md) for the details,
and for the Grafana dashboard you get with `docker compose --profile observability up -d`.

## Project layout

```
backend/         Spring Boot API
frontend/        React app
observability/   Prometheus, Tempo and Grafana config
docs/            architecture, decisions and reference docs
```

## Docs

- [Architecture](docs/architecture.md): how the pieces fit, the main flows, and what changes when you run more than one instance
- [Decision records](docs/adr/README.md): why things are built the way they are
- [How it works](docs/features.md): the rules for postings, offers, chat, deals, reviews and security
- [API](docs/api.md), [configuration](docs/configuration.md), [operations](docs/operations.md)

## Not done yet

- Payments. Deals record the agreed amount, but the money moves outside the platform for now. Stripe
  Connect holding funds until a deal completes is the obvious next step.
- Password reset and email verification.
- Instagram and TikTok (both need app review from Meta and TikTok before they share data).
- Text search for postings, image thumbnails and push notifications.
- The Twitch and YouTube connections have only been tested against a fake OAuth server. They need real
  app credentials and a manual try before relying on them.

## License

[MIT](LICENSE)
