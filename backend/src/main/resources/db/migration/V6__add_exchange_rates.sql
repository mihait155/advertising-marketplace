CREATE TABLE exchange_rates
(
    currency      VARCHAR(3) PRIMARY KEY,
    units_per_eur NUMERIC(19, 8) NOT NULL,
    rate_date     DATE           NOT NULL,
    fetched_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_exchange_rates_positive CHECK (units_per_eur > 0)
);

INSERT INTO exchange_rates (currency, units_per_eur, rate_date)
VALUES ('EUR', 1, CURRENT_DATE);

ALTER TABLE postings
    ADD COLUMN pay_from_eur NUMERIC(14, 2),
    ADD COLUMN pay_to_eur   NUMERIC(14, 2);

UPDATE postings
SET pay_from_eur = pay_from,
    pay_to_eur   = pay_to
WHERE pay_currency = 'EUR';

CREATE INDEX idx_postings_pay_from_eur ON postings (pay_from_eur);
CREATE INDEX idx_postings_pay_to_eur ON postings (pay_to_eur);
