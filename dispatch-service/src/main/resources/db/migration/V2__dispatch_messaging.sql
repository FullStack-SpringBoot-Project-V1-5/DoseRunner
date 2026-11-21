CREATE TABLE dispatch_message_lock (id integer PRIMARY KEY CHECK (id = 1));
INSERT INTO dispatch_message_lock VALUES (1);

CREATE TABLE dispatch_inbox (
    event_id uuid PRIMARY KEY,
    order_id uuid NOT NULL,
    payload_hash varchar(64) NOT NULL,
    processed_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE dispatch_command_sequence (
    robot_id varchar(64) PRIMARY KEY REFERENCES robots(id),
    last_value integer NOT NULL CHECK (last_value > 0)
);
CREATE TABLE dispatch_outbox (
    id bigserial PRIMARY KEY,
    mission_id uuid NOT NULL UNIQUE REFERENCES missions(id),
    order_id uuid NOT NULL,
    robot_id varchar(64) NOT NULL REFERENCES robots(id),
    command_seq integer NOT NULL CHECK (command_seq > 0),
    trace_id text NOT NULL,
    assigned bytea NOT NULL,
    command bytea NOT NULL,
    sent_at timestamptz,
    UNIQUE(robot_id, command_seq)
);
CREATE INDEX dispatch_outbox_pending ON dispatch_outbox(id) WHERE sent_at IS NULL;
