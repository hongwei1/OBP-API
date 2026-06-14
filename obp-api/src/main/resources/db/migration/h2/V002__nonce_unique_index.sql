-- Prevent OAuth nonce replay: enforce uniqueness of (value, consumerkey, tokenkey, timestamp_c).
-- The original Lift Mapper schema had no DB-level uniqueness guarantee; the application performed
-- a SELECT COUNT then INSERT (read-check-write), leaving a TOCTOU window where two concurrent
-- requests with the same nonce could both pass the check and both be inserted.
-- This index closes that window: a duplicate INSERT now fails with a constraint violation,
-- which DoobieNonceProvider.createNonce wraps in tryo and returns as Failure.
--
-- TIMESTAMP_C must be NOT NULL for the unique index to be effective: SQL treats NULL as
-- distinct from every value including itself, so multiple rows with NULL TIMESTAMP_C all pass
-- the constraint and nonces with no timestamp could be replayed without limit.
-- Backfill any existing NULLs with the Unix epoch (a harmless sentinel for historic rows),
-- then tighten the column before creating the index.
UPDATE "PUBLIC"."NONCE" SET "TIMESTAMP_C" = TIMESTAMP '1970-01-01 00:00:00.000000' WHERE "TIMESTAMP_C" IS NULL;
ALTER TABLE "PUBLIC"."NONCE" ALTER COLUMN "TIMESTAMP_C" TIMESTAMP NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS nonce_replay_prevention
    ON "PUBLIC"."NONCE"("VALUE", "CONSUMERKEY", "TOKENKEY", "TIMESTAMP_C");
