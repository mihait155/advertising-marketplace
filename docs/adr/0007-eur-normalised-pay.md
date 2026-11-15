# 0007. Store EUR-normalised pay for cross-currency filtering

Status: accepted

## Context

Postings and offers can be priced in EUR, USD, GBP or RON. Users filter and sort by pay in their own
currency ("between 1,000 and 3,000 USD"), and results must be paginated in the database.

## Decision

Keep the original amount and currency as the source of truth, and also store `pay_from_eur` and `pay_to_eur`
on each posting. Daily ECB reference rates are stored in `exchange_rates`. The EUR columns are computed when a
posting is saved and recomputed for all postings in one `UPDATE` whenever new rates arrive. A filter in any
currency is converted to EUR once and becomes an indexed range query. Amounts are `NUMERIC` and use
`BigDecimal` with each currency's decimal places.

## Consequences

- Filtering and sorting stay plain SQL with an index, and paginate correctly.
- Between rate updates, conversions use the previous day's rate. That's acceptable for discovery; agreed
  amounts on deals are always in the offer's own currency and never converted.
- A recompute touches every posting once a day; cheap at this size, and batchable if it grows.

## Alternatives considered

- **Convert in the query** (join on rates). Correct, but the conversion has to happen per row, which defeats
  the index.
- **Convert in application code after fetching.** Breaks pagination and sorting.
