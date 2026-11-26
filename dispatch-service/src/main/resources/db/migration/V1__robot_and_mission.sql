CREATE TABLE robots (
    id varchar(64) PRIMARY KEY CHECK (length(trim(id)) > 0),
    x integer NOT NULL CHECK (x >= 0),
    y integer NOT NULL CHECK (y >= 0),
    battery integer NOT NULL CHECK (battery BETWEEN 0 AND 100),
    status varchar(16) NOT NULL CHECK (status IN ('IDLE', 'BUSY', 'OFFLINE'))
);

CREATE TABLE missions (
    id uuid PRIMARY KEY,
    order_id uuid NOT NULL,
    robot_id varchar(64) NOT NULL REFERENCES robots(id),
    status varchar(16) NOT NULL CHECK (status IN ('ASSIGNED', 'COMPLETED', 'ABORTED'))
);

-- Synthetic demo fleet; these positions do not define the hospital map.
INSERT INTO robots (id, x, y, battery, status) VALUES
    ('R-01', 0, 0, 100, 'IDLE'),
    ('R-02', 5, 0, 80, 'IDLE'),
    ('R-03', 0, 5, 60, 'IDLE');
