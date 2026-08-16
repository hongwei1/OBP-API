-- Prevent OAuth nonce replay: enforce uniqueness of (value, consumerkey, tokenkey, timestamp_c).
-- See h2/V002 for full rationale. TIMESTAMP_C is made NOT NULL before the index is created so
-- that NULL rows (which are distinct from each other under SQL semantics) cannot bypass the check.
UPDATE nonce SET timestamp_c = '1970-01-01 00:00:00' WHERE timestamp_c IS NULL;
ALTER TABLE nonce ALTER COLUMN timestamp_c DATETIME NOT NULL;
CREATE UNIQUE INDEX nonce_replay_prevention
    ON nonce(value, consumerkey, tokenkey, timestamp_c);
