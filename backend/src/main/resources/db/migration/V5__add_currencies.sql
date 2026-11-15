ALTER TABLE postings
    ADD COLUMN pay_currency VARCHAR(3);

UPDATE postings
SET pay_currency = 'EUR'
WHERE pay_from IS NOT NULL
   OR pay_to IS NOT NULL;

ALTER TABLE postings
    ADD CONSTRAINT ck_postings_pay_currency_format CHECK (pay_currency ~ '^[A-Z]{3}$'),
    ADD CONSTRAINT ck_postings_pay_currency_present CHECK ((pay_currency IS NULL) = (pay_from IS NULL AND pay_to IS NULL));

ALTER TABLE offers
    ADD COLUMN currency VARCHAR(3);

UPDATE offers o
SET currency = COALESCE((SELECT p.pay_currency FROM postings p WHERE p.id = o.posting_id), 'EUR');

ALTER TABLE offers
    ALTER COLUMN currency SET NOT NULL,
    ADD CONSTRAINT ck_offers_currency_format CHECK (currency ~ '^[A-Z]{3}$');
