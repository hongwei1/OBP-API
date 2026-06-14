-- Prevent OAuth nonce replay: enforce uniqueness of (value, consumerkey, tokenkey, timestamp_c).
-- The original Lift Mapper schema had no DB-level uniqueness guarantee; the application performed
-- a SELECT COUNT then INSERT (read-check-write), leaving a TOCTOU window where two concurrent
-- requests with the same nonce could both pass the check and both be inserted.
-- This index closes that window: a duplicate INSERT now fails with a constraint violation,
-- which DoobieNonceProvider.createNonce wraps in tryo and returns as Failure.
CREATE UNIQUE INDEX IF NOT EXISTS nonce_replay_prevention
    ON "PUBLIC"."NONCE"("VALUE", "CONSUMERKEY", "TOKENKEY", "TIMESTAMP_C");
