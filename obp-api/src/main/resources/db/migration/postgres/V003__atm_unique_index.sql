-- Enforce uniqueness of (mbankid, matmid) on MAPPEDATM. See h2/V003 for full rationale.
-- The Lift Schemifier owned this UniqueIndex before MappedAtm was removed; Flyway now owns it.
CREATE UNIQUE INDEX IF NOT EXISTS mappedatm_mbankid_matmid
    ON mappedatm(mbankid, matmid);
