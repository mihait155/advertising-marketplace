# 0001. Modular monolith instead of microservices

Status: accepted

## Context

The product has about ten business capabilities (users, postings, offers, deals, reviews, messaging, images,
social accounts, money). They are tightly linked: accepting an offer creates a deal and posts a system
message in the same transaction, and a profile reads reviews, deals and social accounts together. One
person builds and operates it.

## Decision

Ship one Spring Boot application, with packages split by capability (`offer`, `deal`, `messaging`, …), each
owning its entities, repository, service and controller. Packages call each other through services, not
through each other's repositories.

## Consequences

- Cross-capability operations are plain local transactions. Accept-offer → create-deal → system message
  either all happen or none do, without sagas or outbox tables.
- One build, one deploy, one database to back up, one set of dashboards.
- Boundaries are enforced by convention, not by the build. If a capability needs to be split out later, the
  service interfaces are the seams. A next step would be to enforce them with Spring Modulith or ArchUnit.
- Everything scales together. That's acceptable because the hot paths (browse, messaging) are cheap and the
  API is stateless.

## Alternatives considered

- **Microservices per capability.** Independent deploys and scaling, but distributed transactions for the
  offer→deal flow, a broker, service discovery and far more operations for one person. The scale doesn't
  justify any of it.
