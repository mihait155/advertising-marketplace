# 0002. PostgreSQL LISTEN/NOTIFY for real-time fan-out

Status: accepted

## Context

Messages, read receipts and typing indicators must reach the other participant immediately, over SSE or
STOMP. With more than one API instance, the recipient may be connected to a different instance than the
sender. Events must never announce data that was rolled back.

## Decision

Publish each conversation event with `pg_notify('conversation_events', payload)` inside the same transaction
as the change. Every instance holds one dedicated JDBC connection that `LISTEN`s, and pushes events to the
clients connected to it. Payloads carry ids; the receiving instance loads the message itself.
`ConversationEventPublisher` is an interface, so the transport can be swapped.

## Consequences

- Delivery is transactional for free: PostgreSQL only delivers a notification when its transaction commits.
- No extra infrastructure (Redis, Kafka, RabbitMQ) to run, secure and monitor.
- Delivery is at-most-once, and an instance that is disconnected while an event fires misses it. The SPA
  treats real time as a hint and refetches conversations, messages and unread counts when its WebSocket
  reconnects, so nothing is lost permanently.
- The 8 KB payload limit and single-database throughput cap the volume. That's fine at marketplace scale,
  and the interface is the seam to move to a broker.
- The listener needs reconnect logic, which is implemented and logged.

## Alternatives considered

- **Redis pub/sub.** Similar semantics, but not transactional with the data, and it's another system.
- **Kafka or RabbitMQ.** Durable and replayable, but heavy for chat notifications, and it needs an outbox to
  be transactional.
- **Spring's simple broker only.** Works on one instance and breaks silently on two.
