-- LedgerOne core schema. Every business table carries company_id (tenant key).

CREATE TABLE company (
    id                      UUID PRIMARY KEY,
    name                    VARCHAR(200) NOT NULL,
    base_currency           VARCHAR(3)   NOT NULL DEFAULT 'USD',
    fiscal_year_start_month INT          NOT NULL DEFAULT 1,
    lock_date               DATE,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE app_user (
    id            UUID PRIMARY KEY,
    email         VARCHAR(200) NOT NULL UNIQUE,
    name          VARCHAR(200) NOT NULL,
    password_hash VARCHAR(200) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE company_member (
    company_id UUID        NOT NULL REFERENCES company (id),
    user_id    UUID        NOT NULL REFERENCES app_user (id),
    role       VARCHAR(30) NOT NULL,
    PRIMARY KEY (company_id, user_id)
);

CREATE TABLE account (
    id         UUID PRIMARY KEY,
    company_id UUID         NOT NULL REFERENCES company (id),
    code       VARCHAR(20)  NOT NULL,
    name       VARCHAR(200) NOT NULL,
    type       VARCHAR(20)  NOT NULL,
    subtype    VARCHAR(40)  NOT NULL,
    parent_id  UUID REFERENCES account (id),
    system_key VARCHAR(40),
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version    BIGINT       NOT NULL DEFAULT 0,
    UNIQUE (company_id, code)
);
CREATE UNIQUE INDEX account_system_key_uk ON account (company_id, system_key) WHERE system_key IS NOT NULL;

CREATE TABLE customer (
    id              UUID PRIMARY KEY,
    company_id      UUID         NOT NULL REFERENCES company (id),
    display_name    VARCHAR(200) NOT NULL,
    email           VARCHAR(200),
    phone           VARCHAR(50),
    billing_address VARCHAR(500),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version         BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE vendor (
    id           UUID PRIMARY KEY,
    company_id   UUID         NOT NULL REFERENCES company (id),
    display_name VARCHAR(200) NOT NULL,
    email        VARCHAR(200),
    phone        VARCHAR(50),
    address      VARCHAR(500),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version      BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE journal_entry (
    id             UUID PRIMARY KEY,
    company_id     UUID        NOT NULL REFERENCES company (id),
    number         BIGINT      NOT NULL,
    entry_date     DATE        NOT NULL,
    memo           VARCHAR(500),
    source_type    VARCHAR(30) NOT NULL,
    source_id      UUID,
    reversal_of_id UUID REFERENCES journal_entry (id),
    reversed       BOOLEAN     NOT NULL DEFAULT FALSE,
    created_by     UUID,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (company_id, number)
);

CREATE TABLE journal_line (
    id          UUID PRIMARY KEY,
    entry_id    UUID           NOT NULL REFERENCES journal_entry (id),
    company_id  UUID           NOT NULL REFERENCES company (id),
    line_no     INT            NOT NULL,
    account_id  UUID           NOT NULL REFERENCES account (id),
    debit       NUMERIC(19, 4) NOT NULL DEFAULT 0,
    credit      NUMERIC(19, 4) NOT NULL DEFAULT 0,
    description VARCHAR(500),
    customer_id UUID REFERENCES customer (id),
    vendor_id   UUID REFERENCES vendor (id),
    CHECK (debit >= 0 AND credit >= 0 AND (debit = 0 OR credit = 0))
);
CREATE INDEX journal_line_account_idx ON journal_line (company_id, account_id);

CREATE TABLE invoice (
    id               UUID PRIMARY KEY,
    company_id       UUID           NOT NULL REFERENCES company (id),
    number           BIGINT         NOT NULL,
    customer_id      UUID           NOT NULL REFERENCES customer (id),
    invoice_date     DATE           NOT NULL,
    due_date         DATE           NOT NULL,
    status           VARCHAR(20)    NOT NULL,
    total            NUMERIC(19, 4) NOT NULL DEFAULT 0,
    amount_paid      NUMERIC(19, 4) NOT NULL DEFAULT 0,
    memo             VARCHAR(500),
    journal_entry_id UUID REFERENCES journal_entry (id),
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version          BIGINT         NOT NULL DEFAULT 0,
    UNIQUE (company_id, number)
);

CREATE TABLE invoice_line (
    id          UUID PRIMARY KEY,
    invoice_id  UUID           NOT NULL REFERENCES invoice (id) ON DELETE CASCADE,
    line_no     INT            NOT NULL,
    description VARCHAR(500)   NOT NULL,
    quantity    NUMERIC(19, 4) NOT NULL,
    rate        NUMERIC(19, 4) NOT NULL,
    amount      NUMERIC(19, 4) NOT NULL,
    account_id  UUID           NOT NULL REFERENCES account (id)
);

CREATE TABLE payment (
    id                 UUID PRIMARY KEY,
    company_id         UUID           NOT NULL REFERENCES company (id),
    customer_id        UUID           NOT NULL REFERENCES customer (id),
    invoice_id         UUID           NOT NULL REFERENCES invoice (id),
    payment_date       DATE           NOT NULL,
    amount             NUMERIC(19, 4) NOT NULL,
    deposit_account_id UUID           NOT NULL REFERENCES account (id),
    method             VARCHAR(30),
    reference          VARCHAR(100),
    journal_entry_id   UUID REFERENCES journal_entry (id),
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE TABLE bill (
    id               UUID PRIMARY KEY,
    company_id       UUID           NOT NULL REFERENCES company (id),
    reference        VARCHAR(100),
    vendor_id        UUID           NOT NULL REFERENCES vendor (id),
    bill_date        DATE           NOT NULL,
    due_date         DATE           NOT NULL,
    status           VARCHAR(20)    NOT NULL,
    total            NUMERIC(19, 4) NOT NULL DEFAULT 0,
    amount_paid      NUMERIC(19, 4) NOT NULL DEFAULT 0,
    memo             VARCHAR(500),
    journal_entry_id UUID REFERENCES journal_entry (id),
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version          BIGINT         NOT NULL DEFAULT 0
);

CREATE TABLE bill_line (
    id          UUID PRIMARY KEY,
    bill_id     UUID           NOT NULL REFERENCES bill (id) ON DELETE CASCADE,
    line_no     INT            NOT NULL,
    description VARCHAR(500)   NOT NULL,
    amount      NUMERIC(19, 4) NOT NULL,
    account_id  UUID           NOT NULL REFERENCES account (id)
);

CREATE TABLE bill_payment (
    id               UUID PRIMARY KEY,
    company_id       UUID           NOT NULL REFERENCES company (id),
    vendor_id        UUID           NOT NULL REFERENCES vendor (id),
    bill_id          UUID           NOT NULL REFERENCES bill (id),
    payment_date     DATE           NOT NULL,
    amount           NUMERIC(19, 4) NOT NULL,
    bank_account_id  UUID           NOT NULL REFERENCES account (id),
    journal_entry_id UUID REFERENCES journal_entry (id),
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE TABLE audit_log (
    id         UUID PRIMARY KEY,
    company_id UUID         NOT NULL,
    user_id    UUID,
    entity     VARCHAR(50)  NOT NULL,
    entity_id  UUID,
    action     VARCHAR(30)  NOT NULL,
    detail     VARCHAR(1000),
    at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);
