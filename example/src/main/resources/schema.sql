CREATE TABLE IF NOT EXISTS customer (
    id          BIGSERIAL PRIMARY KEY,
    name        TEXT NOT NULL,
    region      TEXT NOT NULL,
    signup_date DATE NOT NULL
);

CREATE TABLE IF NOT EXISTS invoice (
    id           BIGSERIAL PRIMARY KEY,
    customer_id  BIGINT NOT NULL REFERENCES customer(id),
    amount_cents BIGINT NOT NULL,
    issued_at    TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_customer_region ON customer (region);
CREATE INDEX IF NOT EXISTS idx_invoice_customer ON invoice (customer_id);
