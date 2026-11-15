# API reference

All endpoints are under `/api/v1` and return JSON. Errors are RFC 9457 problem details with a `traceId`. Behaviour behind these endpoints is described in [features.md](features.md).

Auth (`/api/v1/auth`):

| Method | Path               | Auth   | Description |
|--------|--------------------|--------|-------------|
| `POST` | `/signup`          | public | Register as `MARKETER` or `CUSTOMER`. `201` + tokens; `409` if email taken. |
| `POST` | `/login`           | public | `{email, password}` → tokens. `401` bad credentials, `429` locked out. |
| `POST` | `/refresh`         | public | Cookie + `X-Requested-With`, or `{refreshToken}` → new tokens (old one is consumed). `401` if invalid/expired/used. |
| `POST` | `/logout`          | public | Cookie + `X-Requested-With`, or `{refreshToken}` → `204`, clears the cookie. Works even with an expired access token. |
| `POST` | `/logout-all`      | bearer | `204`. Ends all of the caller's sessions. |
| `GET`  | `/me`              | bearer | The current user. |
| `POST` | `/change-password` | bearer | `{currentPassword, newPassword}` → `204`; ends all sessions. `400` if current password is wrong. |

Token response:

```json
{
  "tokenType": "Bearer",
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "accessTokenExpiresAt": "2026-11-04T09:43:47Z",
  "refreshToken": null,
  "refreshTokenExpiresAt": "2026-12-04T09:28:47Z",
  "user": { "id": "...", "fullName": "Jane Doe", "email": "jane@example.com", "phone": null, "userType": "MARKETER", "createdAt": "...", "updatedAt": "..." }
}
```

`refreshToken` is only filled in when the request has `X-Token-Delivery: body`; browsers get it as the cookie.

Users (`/api/v1/users`, all require a bearer token):

| Method   | Path     | Who            | Description |
|----------|----------|----------------|-------------|
| `POST`   | `/`      | admin          | Create a user of any type (body includes `password`). `201` + `Location`. |
| `GET`    | `/{id}`  | any user       | Get a user (`404` if missing). |
| `GET`    | `/`      | any user       | List users, paginated. Query: `userType`, `page`, `size`, `sort`. |
| `PUT`    | `/{id}`  | self or admin  | Replace `fullName`, `email`, `phone`, `userType` (only admins may change `userType`). |
| `DELETE` | `/{id}`  | self or admin  | Delete a user (`204`); their sessions are deleted too. `409` while they have active deals. |
| `GET`    | `/{id}/profile` | any user | Public profile: rating, completed deals, verified reach, connected accounts, recent reviews. |
| `GET`    | `/{id}/reviews` | any user | Visible reviews of the user, paginated. |

Images (`/api/v1/images`, bearer token):

| Method | Path    | Description |
|--------|---------|-------------|
| `POST` | `/`     | Upload (multipart `file`). `201`; `400` empty, `413` too large, `415` not an image, `422` infected, `503` scanner/storage down. |
| `GET`  | `/{id}` | Metadata + fresh signed URL. Unembedded images are only visible to their owner and admins. |

Postings (`/api/v1/postings`, bearer token):

| Method   | Path    | Who                | Description |
|----------|---------|--------------------|-------------|
| `POST`   | `/`            | any user         | `{category, status?, title, text, payFrom?, payTo?, payCurrency?}`. `403` if the category doesn't match your user type. |
| `GET`    | `/{id}`        | any user         | One posting with creator and images (drafts: creator and admins only). |
| `GET`    | `/`            | any user         | Browse, newest first. Query: `category`, `status`, `creatorId`, `minPay`, `maxPay`, `currency`, `page`, `size`, `sort` (`createdAt`, `updatedAt`, `title`, `pay`). Other users' drafts are never listed. |
| `PUT`    | `/{id}`        | creator or admin | Replace category, title, text and pay range. `409` if the posting is closed. |
| `PATCH`  | `/{id}/status` | creator or admin | `{status}`. `409` for a transition that isn't allowed. |
| `DELETE` | `/{id}`        | creator or admin | `204`. Also deletes its offers. |

Offers (bearer token):

| Method  | Path                            | Who                                  | Description |
|---------|---------------------------------|--------------------------------------|-------------|
| `POST`  | `/api/v1/postings/{id}/offers`  | the other side                       | `{text, priceFrom, priceTo, currency?}` → `201`, status `PENDING`. |
| `GET`   | `/api/v1/postings/{id}/offers`  | any user                             | Posting creator and admins see all offers; others see only their own. Query: `status`, paging. |
| `GET`   | `/api/v1/offers/mine`           | any user                             | Offers you made, across postings. Query: `status`, paging. |
| `GET`   | `/api/v1/offers/{id}`           | offerer, posting creator, admin      | One offer, with a posting summary and the offerer. |
| `PUT`   | `/api/v1/offers/{id}`           | offerer                              | Revise text and price of a pending offer. |
| `PATCH` | `/api/v1/offers/{id}/status`    | see rules above                      | `{status, amount?}`: `ACCEPTED` (starts a deal; `amount` required for a range), `REJECTED` or `WITHDRAWN`. `409` if the offer is no longer pending. |

Deals (`/api/v1/deals`, participants and admins):

| Method | Path                     | Who        | Description |
|--------|--------------------------|------------|-------------|
| `GET`  | `/`                      | any user   | Your deals, newest first. Query: `status`, paging. |
| `GET`  | `/{id}`                  | participant, admin | Deal with deliveries and your review state. |
| `POST` | `/{id}/deliveries`       | marketer   | `{links, note?}`. `409` unless `IN_PROGRESS`. |
| `POST` | `/{id}/approve`          | advertiser | Completes the deal. |
| `POST` | `/{id}/request-changes`  | advertiser | `{feedback}`. Back to `IN_PROGRESS`. |
| `POST` | `/{id}/cancel`           | participant, admin | `{reason}`. |
| `POST` | `/{id}/reviews`          | participant | `{rating, text?}` once the deal is completed. `409` if already reviewed. |

Verified accounts (`/api/v1/social`):

| Method   | Path                    | Who      | Description |
|----------|-------------------------|----------|-------------|
| `GET`    | `/providers`            | any user | Which providers are configured. |
| `POST`   | `/{provider}/authorize` | marketer | `{url}` to send the browser to. |
| `POST`   | `/connect`              | marketer | `{code, state}` from the callback. `409` if the channel belongs to another account. |
| `GET`    | `/accounts`             | marketer | Your connected accounts. |
| `DELETE` | `/accounts/{id}`        | owner    | Disconnect. |

Messaging (bearer token, except where noted):

| Method   | Path                                         | Description |
|----------|----------------------------------------------|-------------|
| `POST`   | `/api/v1/postings/{id}/conversations`        | `{text?}`. Start (`201`) or get the existing (`200`) conversation with the posting's creator. |
| `GET`    | `/api/v1/conversations`                      | Your conversations, most recent activity first, with unread counts. Query: `postingId`, paging. |
| `GET`    | `/api/v1/conversations/unread-count`         | Total unread messages and conversations, for a badge. |
| `GET`    | `/api/v1/conversations/{id}`                 | One conversation. |
| `GET`    | `/api/v1/conversations/{id}/messages`        | History, newest first. Query: `limit` (≤ 100), `before`. |
| `POST`   | `/api/v1/conversations/{id}/messages`        | `{text?, imageIds?}` → `201`. |
| `POST`   | `/api/v1/conversations/{id}/read`            | Mark everything as read. |
| `PUT`    | `/api/v1/messages/{id}`                      | `{text}`. Edit your message. |
| `DELETE` | `/api/v1/messages/{id}`                      | Delete your message. |
| `POST`   | `/api/v1/conversations/stream-tickets`       | Short-lived ticket for the SSE stream. |
| `GET`    | `/api/v1/conversations/events?ticket=…`      | SSE stream (ticket or bearer). |
| `GET/PUT/DELETE` | `/api/v1/blocks`, `/api/v1/blocks/{userId}` | List, block, unblock. |
| `POST`   | `/api/v1/messages/{id}/reports`              | `{reason}`. Report a message sent to you. |
| `GET/PUT`| `/api/v1/users/me/notification-settings`     | `{emailMessageNotifications}`. |
| `GET`    | `/api/v1/admin/reports`, `/api/v1/admin/reports/{id}` | Admin: reports by `status`. |
| `POST`   | `/api/v1/admin/reports/{id}/resolve`         | Admin: `{resolution: DISMISSED or MESSAGE_DELETED}`. |
| `GET`    | `/api/v1/admin/conversations/{id}/messages`  | Admin: history of a reported conversation. |

`GET /livez` and `GET /readyz` are public. Everything else under actuator is on the management port (see
[operations.md](operations.md)).

Signup payload:

```json
{
  "fullName": "Jane Doe",
  "email": "jane@example.com",
  "phone": "+40 712 345 678",
  "userType": "MARKETER",
  "password": "Sup3r-secret"
}
```

`phone` is optional. Emails are trimmed, lower-cased and must be unique. Errors are returned as RFC 9457
problem details; validation errors include a field → message `errors` map.
