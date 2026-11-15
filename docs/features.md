# How it works

The rules behind each part of the product. For the big picture see [architecture.md](architecture.md); for endpoints see [api.md](api.md).

## Security model

- **Stateless JWT auth.** Login/signup return a short-lived **access token** (JWT, HS256, 15 min) and a
  long-lived **refresh token** (opaque, 30 days). Send the access token as `Authorization: Bearer <token>`.
- **Refresh token in an httpOnly cookie.** For browsers the refresh token is set as `am_refresh`
  (`HttpOnly`, `Secure`, `SameSite=Strict`, path `/api/v1/auth`) and left out of the JSON body, so script
  injected into the page can't read it. The access token lives only in memory. `/refresh` and `/logout`
  read the cookie and additionally require an `X-Requested-With` header, which a cross-site form can't send.
  Native clients send `X-Token-Delivery: body` to get the refresh token in the body instead and pass it
  back as `{refreshToken}`. Set `AUTH_COOKIE_SECURE=false` only if you serve the app over plain HTTP on a
  host other than `localhost`.
- **Refresh token rotation.** `/auth/refresh` consumes the refresh token and returns a new pair. Only a
  SHA-256 hash of each refresh token is stored. If an already-rotated token is presented again (a sign it
  was stolen), **all** of that user's sessions are revoked.
- **Logout** revokes the refresh token (that device); **logout-all** revokes every refresh token of the user.
  Access tokens can't be revoked and simply expire within 15 minutes.
- **Passwords** are hashed with BCrypt (via `DelegatingPasswordEncoder`, stored as `{bcrypt}…`). Policy:
  8–64 characters, at least one letter and one digit (and ≤ 72 bytes, the BCrypt limit).
- **Brute-force protection.** Failed logins are counted in PostgreSQL (so it works across instances) under
  two keys: email + client IP (5 failures per 15 minutes) and email alone (20 per 15 minutes). The first stops
  one attacker without letting them lock the real owner out from elsewhere; the second caps distributed
  guessing against one account. Over either limit: `429` + `Retry-After`. Unknown emails and wrong passwords
  get the same `401` and take the same time, so responses don't reveal which emails are registered. Behind a
  proxy, the client IP comes from `X-Forwarded-For` (`server.forward-headers-strategy: native`), so only
  expose the backend through a proxy you control.
- **Roles** map from `userType`: `ROLE_MARKETER`, `ROLE_CUSTOMER`, `ROLE_ADMIN`. Self-signup can't create
  admins; the first admin is created from `ADMIN_EMAIL`/`ADMIN_PASSWORD`, and admins can create more.
- **Authorization:** any authenticated user can read users (to browse the marketplace); users can update or
  delete only themselves and can't change their own `userType`; admins can do everything.
- 401/403 responses are RFC 9457 problem details; standard security headers (CSP, `X-Frame-Options: DENY`,
  `nosniff`, no-cache) are set and CORS is limited to `CORS_ALLOWED_ORIGINS`.

## Postings and images

A **posting** has a `category`, a `status`, a `title`, a `text`, an optional pay range
(`payFrom` / `payTo` / `payCurrency`) and a creator (the user who published it).

- `MARKETER_POSTING`: an offer from a marketer (influencer, streamer…). Only `MARKETER` users (and admins) can
  publish these.
- `CUSTOMER_POSTING`: a request from someone who needs advertising. Only `CUSTOMER` users (and admins).

**Status:** `DRAFT → OPEN → CLOSED`, and a closed posting can be reopened (`CLOSED → OPEN`). New postings are
`DRAFT` unless created with `"status": "OPEN"`. Drafts (and the images in them) are only visible to their
creator and admins. Closed postings stay visible but can't be edited and don't accept new offers.

**Pay range:** `payFrom` and `payTo` are optional, non-negative and `payFrom ≤ payTo`. Whenever an amount is
set, `payCurrency` is required (and it isn't allowed without one).

**Currencies:** ISO 4217 codes from the configured list (`SUPPORTED_CURRENCIES`, default `EUR,USD,GBP,RON`),
case-insensitive and stored upper-case. Amounts can't have more decimals than the currency allows (2 for these).
`GET /api/v1/currencies` returns the list with names, symbols, decimals and the current rate, for building a
picker.

**Exchange rates:** the European Central Bank's daily reference rates (EUR base) are fetched at startup and
every weekday at 17:15 Frankfurt time (`EXCHANGE_RATES_URL` overrides the source). If a fetch fails, the
previous rates stay in use. Each posting also stores its pay range converted to EUR; these copies are updated
when the posting is saved and recomputed for all postings whenever new rates arrive. They power:

- **Pay filter:** `GET /api/v1/postings?minPay=…&maxPay=…&currency=USD` (currency defaults to EUR). A posting
  matches when its pay range overlaps the requested one; open-ended ranges ("from 500", "up to 300") count as
  unbounded on the missing side, and postings without pay are excluded.
- **Pay sort:** `sort=pay,desc` (highest top of range first) or `sort=pay,asc` (lowest bottom of range first);
  postings without pay come last. Sorting is limited to `createdAt`, `updatedAt`, `title` and `pay`.

The text is Markdown-style and can **embed photos**:

1. Upload each photo: `POST /api/v1/images` (multipart, field `file`). The upload pipeline is
   size check (10 MB) → **ClamAV scan** of every byte → format check from the file's magic bytes (JPEG, PNG,
   GIF, WebP; SVG is refused) → **metadata stripped** (EXIF/GPS, XMP, IPTC, comments; JPEG orientation and
   ICC colour profiles are kept) → stored in the **private S3 bucket**. Nothing is written to S3 unless the
   scan finished and came back clean; if ClamAV is unreachable the upload fails with `503` (fail closed).
   Infected files get `422`, and the detection is logged with the uploader's id. Each user may upload
   `IMAGE_MAX_UPLOADS_PER_HOUR` images per hour (default 60); beyond that, `429` with `Retry-After`.
2. Reference the returned id in the text: `![caption](image:<id>)`. Only your own, not-yet-used uploads can be
   embedded, and external image URLs are rejected (no hotlinked tracking pixels or unscanned content).
3. Posting responses list the embedded images in an `images` array, each with a **signed URL**
   (valid 1 hour). The bucket itself is never public. To render, replace `image:<id>` with the matching `url`.

Images that are never embedded, removed from a posting during an edit, or whose posting or owner was deleted
are deleted from S3 and the database by an hourly cleanup job after 24 hours.

## Offers

An **offer** is a proposal on a posting: it links the posting and the user making it, and has a `text`,
a `status` and a required price range (`priceFrom ≤ priceTo`; equal for a fixed price) with a `currency`.

- Offers come from **the other side**: customers make offers on `MARKETER_POSTING`s, marketers on
  `CUSTOMER_POSTING`s. Nobody can offer on their own posting, admins can't make offers, and only `OPEN`
  postings accept offers.
- If the posting has a pay currency, the offer must use it (it's filled in when omitted). If the posting has no
  pay range, the offer must name its own currency. A posting's currency can't change while it has pending
  offers in another currency (`409`).
- One **pending** offer per user per posting (enforced in the database too); withdraw it to make a new one.
- `PENDING` is the only state that can change:
  - the posting's creator **accepts** or **rejects** it (accepting needs the posting to be `OPEN`),
  - the offerer **withdraws** it or edits its text and price (while the posting is `OPEN`),
  - admins can do any of these for moderation.
- Offers are private: only the offerer, the posting's creator and admins can see them. Others get `404`.
- Offers are deleted together with their posting or the user who made them.

## Messaging

Marketers and customers talk in **conversations**. There is one conversation per posting and counterpart
(the person on the other side of the posting's creator). It's started with "Contact" on an `OPEN` posting, or
automatically when an offer is made. The same rule as offers applies: only the other side can start one, and
admins can't.

- **Messages** have text (up to 5,000 characters) and/or up to 10 photos, which go through the same upload
  pipeline as postings (ClamAV scan, then private S3). Photos in a conversation are only visible to its two
  participants. Senders can edit and delete their own messages. Deletes are soft: the text and photos are
  removed, and the message shows as deleted.
- **System messages** record events in the timeline: offer made, updated, accepted, rejected or withdrawn, and
  posting closed or reopened. The whole negotiation is in one place.
- **Unread counts** come from per-participant read markers (`POST /conversations/{id}/read`), which also act as
  read receipts (`otherParticipantLastReadAt`).
- **History** is paged with a cursor: `GET /conversations/{id}/messages?limit=50&before=<messageId>`, newest
  first, with `nextBefore` for the next page.

**Real time.** Events (`MESSAGE_CREATED`, `MESSAGE_UPDATED`, `MESSAGE_DELETED`, `CONVERSATION_READ`, `TYPING`)
are published with PostgreSQL `NOTIFY` in the same transaction as the change, so they are only sent if it
commits. Every backend instance `LISTEN`s and delivers to the clients connected to it, so several instances
work without extra infrastructure. Clients can use either:

- **Server-Sent Events:** `GET /api/v1/conversations/events`. Browsers' `EventSource` can't send headers, so get
  a 60-second ticket first with `POST /api/v1/conversations/stream-tickets` and pass `?ticket=…`; a bearer
  header also works. Streams are closed when an instance shuts down, and `EventSource` reconnects.
- **WebSocket (STOMP):** connect to `/ws` with an `Authorization: Bearer …` header in the `CONNECT` frame,
  subscribe to `/user/queue/conversation-events`, and send typing indicators to
  `/app/conversations/{id}/typing`. Clients can only subscribe to their own queues.

**Email notifications.** Every minute a job emails users about messages they haven't read after 5 minutes
(`EMAIL_NOTIFICATION_DELAY`), one email per conversation listing the latest messages and a link to
`FRONTEND_URL/conversations/{id}`. Each message is notified at most once. Instances coordinate with
`FOR UPDATE SKIP LOCKED`, so nothing is sent twice. Users can turn it off with
`PUT /api/v1/users/me/notification-settings`. Locally, the emails show up in Mailpit at http://localhost:8025.

**Safety.**
- Rate limit: 30 messages per minute per user (`MESSAGING_MAX_PER_MINUTE`), `429` with `Retry-After`.
- Blocking: `PUT/DELETE /api/v1/blocks/{userId}` stops messages and offers in both directions.
- Reporting: `POST /api/v1/messages/{id}/reports`. Admins review in `/api/v1/admin/reports`, can read a
  conversation only once something in it was reported, and resolve with `DISMISSED` or `MESSAGE_DELETED`.
- Deleted accounts: their messages stay, shown as "Deleted user"; new messages to them get `409`.
- Optional contact masking (`MESSAGING_MASK_CONTACT_DETAILS=true`): replaces emails and phone numbers in
  messages with `[contact details hidden]`, to keep deals on the platform. It's off by default, and tuned to
  leave prices, dates and follower counts alone.

## Deals and deliveries

Accepting an offer starts a **deal**. For a price range, the acceptor names the agreed amount
(`PATCH /offers/{id}/status {"status":"ACCEPTED","amount":2500}`); it must lie inside the offer's range and
match the currency's decimals. A fixed price needs no amount. The deal keeps its own copy of the amount,
currency and posting title, so it survives later edits.

```
IN_PROGRESS ──deliver──▶ DELIVERED ──approve / auto-approve──▶ COMPLETED
     ▲                       │
     └──request changes──────┘
IN_PROGRESS ──cancel──▶ CANCELLED     (admins can also cancel a DELIVERED deal)
```

- The marketer submits a delivery: 1–10 `http(s)` links (the VOD, video or post) and an optional note.
- The advertiser approves it or requests changes with feedback; every round is kept as history.
- If the advertiser does nothing for `DEAL_AUTO_APPROVE_AFTER` (7 days), a scheduled job approves it, so a
  marketer is never left waiting forever. Each deal is completed in its own transaction, and optimistic
  locking stops this job and a manual approval from both applying.
- Every step posts a system message in the pair's conversation.
- Postings and accounts with an active deal can't be deleted (`409`).

Payment is not handled yet; see the main README for what's missing.

## Reviews

After a deal completes, both sides can review each other once (1–5 stars, optional text) for 30 days.
Reviews are **double-blind**: neither side sees the other's review until both have submitted, or until 14
days after completion. Neither side can tailor their review to the other's. A user's public profile
(`GET /users/{id}/profile`) shows the average rating, number of completed deals and recent visible reviews.

## Verified audience

Marketers can connect **Twitch** and **YouTube** accounts with OAuth instead of typing in follower counts:

1. `POST /social/{provider}/authorize` returns the provider's consent URL with a signed, user-bound,
   10-minute `state`.
2. The provider redirects to `SOCIAL_REDIRECT_URI` (the frontend), which posts `{code, state}` to
   `/social/connect`. The backend checks the state, exchanges the code, reads the channel's
   followers/subscribers, views and content count, and stores the provider's refresh token encrypted with
   AES-256-GCM.
3. A daily job refreshes the stats. If the user revoked access, the account is flagged for reconnection
   instead of failing silently.

A channel can be linked to only one marketplace account. Only read-only scopes are requested
(`moderator:read:followers`, `youtube.readonly`). The integration is tested against a fake OAuth server
in the integration tests. It has not been run against the real providers in this repository, so register apps
in the Twitch developer console and Google Cloud console, set the variables in [configuration.md](configuration.md), and try one
connection before relying on it.

## Frontend

**What's in it.**
- Sign up as a marketer ("I have an audience") or an advertiser ("I want to advertise"), and log in. The
  access token lives in memory only, and the refresh token in an `HttpOnly` cookie the page can't read. Requests that get a `401`
  refresh once and retry, and concurrent requests share one refresh.
- **Browse**: campaigns (from advertisers) and services (from marketers), opening on the side you can respond
  to. Filters for pay (in any supported currency, converted at ECB rates), status, and sort by newest or pay.
  Filters live in the URL, so a view can be shared.
- **Postings**: a Markdown editor with preview and image upload (scanned by ClamAV on the server, inserted as
  `![caption](image:<id>)`), an optional pay range and currency, saved as a draft or published. The creator
  can publish, close, reopen, edit and delete, and accept or decline offers. Others see an offer form or their
  existing offer (revise or withdraw), and a "Message" button.
- **My postings / My offers** with status tabs.
- **Messages**: an inbox with unread counts, threads with day separators, system messages for offer and deal events,
  "Seen" receipts, live typing indicators, image attachments, editing and deleting your own messages,
  reporting and blocking. It updates live over STOMP (`/ws`), and the nav badge shows total unread.
- **Settings**: profile, password (you stay signed in here and are signed out elsewhere), email
  notifications, blocked people, sign out everywhere, and account deletion.
- **Deals**: accepting an offer asks for the agreed amount within the offered range. The deal page shows
  progress, the marketer's delivery form, approve / request changes for the advertiser, the delivery history,
  cancellation, and the double-blind review form once it's complete.
- **Profiles**: every name links to a public profile with rating, completed deals, verified reach and recent
  reviews. Offer cards show the offerer's rating and reach inline.
- **Connected accounts** (marketers, in Settings): connect Twitch or YouTube through OAuth; stats refresh daily.
- **Admin**: a report queue with the reported conversation, dismiss or delete.

**Design.** Plain CSS Modules on a small set of tokens (`src/styles/tokens.css`): one accent colour, neutral
greys, thin borders instead of shadows, and the Inter typeface, self-hosted. Layouts work down to phone width,
where the inbox becomes a single pane and the filters collapse.

**Production.** `frontend/Dockerfile` builds the app and serves it with nginx (`nginx.conf`). nginx falls back
to the SPA for client-side routes, caches hashed assets for a year, adds security headers, allows 11 MB
uploads, and proxies `/api` (unbuffered, for SSE) and `/ws` (WebSocket upgrade) to the backend.
