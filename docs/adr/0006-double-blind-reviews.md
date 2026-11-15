# 0006. Double-blind reviews

Status: accepted

## Context

On marketplaces where reviews are visible immediately, the second reviewer reacts to the first: retaliation
for a bad review, or a courtesy five stars to get one back. That makes ratings less informative.

## Decision

After a deal completes, each side may review the other once within 30 days. A review stays hidden until the
other side has also reviewed, or 14 days after completion, whichever comes first. The visibility rule is
part of the repository query, so no endpoint can leak a hidden review. The API tells a user only that the
other side *has* reviewed them, not what they wrote.

## Consequences

- Neither side can tailor their review to the other's.
- Ratings appear with a delay when only one side reviews.
- One review per reviewee per deal is enforced by a unique constraint, not just by the service.

## Alternatives considered

- **Immediately visible reviews.** Simpler, but has the bias described above.
- **One-way reviews (advertiser rates marketer).** Loses the signal about advertisers who pay late or move
  the goalposts.
