-- Enforce uniqueness of (mbankid, matmid) on MAPPEDATM.
-- The Lift Schemifier defined this as `UniqueIndex(mBankId, mAtmId)` on the MappedAtm meta-mapper,
-- but the Flyway baseline (V001) only created a plain index on MBANKID. Now that the MappedAtm
-- Lift entity has been removed and Flyway is the sole source of truth for this table's schema,
-- the unique constraint must be expressed here so the ATM upsert (DoobieAtmsProvider.createOrUpdateAtm)
-- still relies on a single logical row per (bank, atm).
CREATE UNIQUE INDEX IF NOT EXISTS mappedatm_mbankid_matmid
    ON "PUBLIC"."MAPPEDATM"("MBANKID", "MATMID");
