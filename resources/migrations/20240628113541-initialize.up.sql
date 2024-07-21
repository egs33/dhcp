CREATE TABLE lease_status (value TEXT PRIMARY KEY);

--;;
INSERT INTO
  lease_status (value)
VALUES
  ('offer'),
  ('lease'),
  ('declined');

--;;
CREATE TABLE lease (
  id bigserial PRIMARY KEY,
  client_id bytea NOT NULL CHECK (LENGTH(client_id) <= 255),
  hw_address bytea NOT NULL CHECK (LENGTH(hw_address) <= 16),
  ip_address bytea NOT NULL CHECK (LENGTH(ip_address) <= 4),
  hostname TEXT NOT NULL,
  lease_time INTEGER NOT NULL,
  status TEXT NOT NULL REFERENCES lease_status (value),
  offered_at TIMESTAMPTZ NOT NULL,
  leased_at TIMESTAMPTZ,
  expired_at TIMESTAMPTZ NOT NULL
);

--;;
CREATE INDEX ON lease (hw_address, ip_address);

--;;
CREATE INDEX ON lease (ip_address);

--;;
CREATE TABLE reservation_source (value TEXT PRIMARY KEY);

--;;
INSERT INTO
  reservation_source (value)
VALUES
  ('config'),
  ('api');

--;;
CREATE TABLE reservation (
  hw_address bytea NOT NULL CHECK (LENGTH(hw_address) <= 16),
  ip_address bytea NOT NULL CHECK (LENGTH(ip_address) <= 4),
  source TEXT NOT NULL REFERENCES reservation_source (value),
  PRIMARY KEY (hw_address, ip_address, source)
);

--;;
CREATE INDEX ON reservation (ip_address);
