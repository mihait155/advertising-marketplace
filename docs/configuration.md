# Configuration

Everything is configured through environment variables. The defaults work for local development against `docker compose`.

## Backend

| Variable               | Default                                          | Notes |
|------------------------|--------------------------------------------------|-------|
| `DB_URL`               | `jdbc:postgresql://localhost:5432/marketplace`   | |
| `DB_USERNAME`          | `marketplace`                                    | |
| `DB_PASSWORD`          | `marketplace`                                    | |
| `SERVER_PORT`          | `8080`                                           | |
| `JWT_SECRET`           | *(empty → random key per startup)*               | Base64, ≥ 256 bits: `openssl rand -base64 32`. **Required** anywhere tokens must survive a restart or work across instances. |
| `ADMIN_EMAIL`          | *(empty)*                                        | With `ADMIN_PASSWORD`, creates the first admin at startup if it doesn't exist. |
| `ADMIN_PASSWORD`       | *(empty)*                                        | |
| `ADMIN_FULL_NAME`      | `Administrator`                                  | |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:5173`    | Comma-separated frontend origins. |
| `S3_BUCKET`            | `marketplace-images`                             | Private bucket for uploaded images. |
| `S3_REGION`            | `eu-central-1`                                   | |
| `S3_ENDPOINT`          | `http://localhost:9090`                          | Local S3Mock. **Set to empty for AWS.** |
| `S3_PUBLIC_ENDPOINT`   | *(empty → `S3_ENDPOINT`)*                        | Host used in signed URLs, if browsers reach storage differently than the backend does. |
| `S3_PATH_STYLE_ACCESS` | `true`                                           | `false` for AWS. |
| `S3_ACCESS_KEY` / `S3_SECRET_KEY` | `local` / `local`                     | Set both to empty on AWS to use the default credential chain (IAM role). |
| `S3_CREATE_BUCKET`     | `true`                                           | Creates the bucket at startup if missing. `false` for AWS. |
| `CLAMAV_HOST` / `CLAMAV_PORT` | `localhost` / `3310`                      | clamd TCP socket. |
| `SUPPORTED_CURRENCIES` | `EUR,USD,GBP,RON`                                | ISO 4217 codes accepted for pay and price ranges. |
| `EXCHANGE_RATES_URL`   | ECB `eurofxref-daily.xml`                        | Source of daily rates (ECB XML format). |
| `EXCHANGE_RATES_REFRESH_ON_STARTUP` | `true`                              | Fetch rates when the app starts. |
| `MAIL_HOST` / `MAIL_PORT` | `localhost` / `1025`                      | SMTP server (Mailpit locally). `MAIL_USERNAME` / `MAIL_PASSWORD` if needed. |
| `EMAIL_NOTIFICATIONS_ENABLED` | `true`                                | Unread-message emails. |
| `EMAIL_FROM`           | `Advertising Marketplace <no-reply@marketplace.local>` | Sender address. |
| `EMAIL_NOTIFICATION_DELAY` | `5m`                                     | How long a message stays unread before it's emailed. |
| `FRONTEND_URL`         | `http://localhost:5173`                          | Base for links in emails. |
| `MESSAGING_MAX_PER_MINUTE` | `30`                                     | Messages per user per minute. |
| `MESSAGING_MASK_CONTACT_DETAILS` | `false`                            | Hide emails and phone numbers in messages. |
| `AUTH_COOKIE_SECURE`   | `true`                                           | `Secure` flag on the refresh cookie. Browsers accept it on `http://localhost`. |
| `IMAGE_MAX_UPLOADS_PER_HOUR` | `60`                                       | Upload limit per user. |
| `DEAL_AUTO_APPROVE_AFTER` | `7d`                                          | A delivery the advertiser doesn't act on is approved automatically after this. |
| `TWITCH_CLIENT_ID` / `TWITCH_CLIENT_SECRET` | *(empty)*                   | Twitch app credentials. Twitch connections are offered only when set. |
| `YOUTUBE_CLIENT_ID` / `YOUTUBE_CLIENT_SECRET` | *(empty)*                 | Google OAuth client with the YouTube Data API v3 enabled. |
| `SOCIAL_REDIRECT_URI`  | `${FRONTEND_URL}/settings/connections/callback`  | Must be registered as the redirect URI in both provider consoles. |
| `SOCIAL_TOKEN_ENCRYPTION_KEY` | *(empty → random key per startup)*        | Base64 AES-256 key for stored provider refresh tokens: `openssl rand -base64 32`. **Required** in production, otherwise daily sync breaks after a restart. |

Example for local development:

```bash
export JWT_SECRET=$(openssl rand -base64 32)
export ADMIN_EMAIL=admin@example.com ADMIN_PASSWORD='Change-me-123'
./mvnw spring-boot:run
```

## Monitoring

| Variable | Default | Notes |
|----------|---------|-------|
| `MANAGEMENT_PORT` | `8081` | Actuator port. Keep it private. |
| `TRACING_EXPORT_ENABLED` | `false` | Send spans over OTLP. Trace ids are generated either way. |
| `OTLP_TRACING_ENDPOINT` | `http://localhost:4318/v1/traces` | Any OTLP/HTTP collector (Tempo, Jaeger, Grafana Cloud, Honeycomb…). |
| `TRACING_SAMPLING_PROBABILITY` | `1.0` | Lower it in production, e.g. `0.1`. |

Set `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` (or `logstash`, `gelf`) for JSON logs.

## Frontend

- `BACKEND_URL`: where the Vite dev server proxies `/api` and `/ws` (default `http://localhost:8080`).
- `E2E_BASE_URL`: run the Playwright tests against an already running app instead of the dev server.
