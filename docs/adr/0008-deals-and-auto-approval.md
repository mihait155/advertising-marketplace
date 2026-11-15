# 0008. Deals as their own aggregate, with auto-approval and no payments yet

Status: accepted

## Context

An accepted offer used to be the end of the product's involvement. Real marketplaces track the work: what
was delivered, whether it was accepted, and a record both sides can point to. Marketers also need protection
from advertisers who simply never respond.

## Decision

- Accepting an offer creates a `Deal` in the same transaction. It snapshots the amount, currency, posting
  title and both parties, so it doesn't depend on the posting or offer staying unchanged.
- For a price range, the acceptor must name an amount inside the range.
- The marketer submits deliveries (links plus a note). The advertiser approves or requests changes. Every
  round is kept as history.
- A delivery not acted on within 7 days is **approved automatically** by a scheduled job. Each deal is
  completed in its own transaction, and an optimistic lock (`@Version`) makes a concurrent manual approval
  or a second instance skip it rather than double-complete it.
- Each transition posts a system message to the pair's conversation.
- Payments are out of scope for now.

## Consequences

- The workflow is a small explicit state machine, tested at the service and API level.
- Without escrow, the platform records the agreement but doesn't guarantee payment. Stripe Connect (hold on
  accept, release on complete, refund on cancel) would attach to exactly these transitions.
- Postings and accounts with active deals can't be deleted, so a counterparty can't vanish mid-deal.

## Alternatives considered

- **Statuses on the offer.** Fewer tables, but mixes negotiation with fulfilment and makes delivery history
  awkward.
- **No auto-approval.** Puts marketers at the mercy of unresponsive advertisers.
