CREATE TABLE drug (
    code        VARCHAR(32)  PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    form        VARCHAR(32)  NOT NULL,
    controlled  BOOLEAN      NOT NULL DEFAULT FALSE,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version     BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE stock_item (
    drug_code   VARCHAR(32)  PRIMARY KEY REFERENCES drug (code),
    available   INTEGER      NOT NULL CHECK (available >= 0),
    reserved    INTEGER      NOT NULL DEFAULT 0 CHECK (reserved >= 0),
    version     BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE ward (
    code         VARCHAR(32)  PRIMARY KEY,
    name         VARCHAR(128) NOT NULL,
    grid_x       INTEGER      NOT NULL CHECK (grid_x >= 0),
    grid_y       INTEGER      NOT NULL CHECK (grid_y >= 0),
    deliverable  BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE medication_order (
    id             UUID         PRIMARY KEY,
    patient_ref    VARCHAR(64)  NOT NULL,
    drug_code      VARCHAR(32)  NOT NULL REFERENCES drug (code),
    qty            INTEGER      NOT NULL CHECK (qty > 0),
    controlled     BOOLEAN      NOT NULL,
    ward_code      VARCHAR(32)  NOT NULL REFERENCES ward (code),
    room           VARCHAR(16),
    status         VARCHAR(24)  NOT NULL CHECK (status IN ('PLACED', 'VALIDATED', 'ASSIGNED', 'PICKED_UP',
                                                          'COMPLETED', 'REJECTED', 'MANUAL_DELIVERY')),
    nurse_id       VARCHAR(64)  NOT NULL,
    pharmacist_id  VARCHAR(64),
    reject_reason  VARCHAR(512),
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    version        BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_medication_order_status_created ON medication_order (status, created_at);
CREATE INDEX idx_medication_order_ward ON medication_order (ward_code);
