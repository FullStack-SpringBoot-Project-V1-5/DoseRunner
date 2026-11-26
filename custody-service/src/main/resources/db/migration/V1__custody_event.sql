-- Custody ledger: one hash-chained, Ed25519-signed link per custody step of an order.
-- prev_hash(n) = BLAKE2b-256(signed bytes of link n-1 || signature n-1); 32 zero bytes for seq 1.

CREATE TABLE custody_event (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id     VARCHAR(64) NOT NULL,
    order_id     VARCHAR(64) NOT NULL,
    seq          BIGINT      NOT NULL CHECK (seq >= 1),
    type         VARCHAR(32) NOT NULL,
    actor_id     VARCHAR(64),
    robot_id     VARCHAR(64),
    payload_hash BYTEA       NOT NULL CHECK (octet_length(payload_hash) = 32),
    prev_hash    BYTEA       NOT NULL CHECK (octet_length(prev_hash) = 32),
    signature    BYTEA       NOT NULL CHECK (octet_length(signature) = 64),
    ts           TIMESTAMPTZ NOT NULL,
    -- an event recorded twice (Artemis at-least-once) must not create a second link
    CONSTRAINT uk_custody_event_event_id UNIQUE (event_id),
    -- two concurrent appends to the same order: one of them fails and is redelivered
    CONSTRAINT uk_custody_event_order_seq UNIQUE (order_id, seq)
);

-- Append-only. This stops mistakes, not an attacker who owns the table (the owner can disable the trigger):
-- the signatures and the hash chain are what make tampering detectable.
CREATE FUNCTION custody_event_append_only() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'custody_event is append-only: % refused', TG_OP;
END;
$$;

CREATE TRIGGER custody_event_no_update_delete
    BEFORE UPDATE OR DELETE ON custody_event
    FOR EACH ROW EXECUTE FUNCTION custody_event_append_only();
