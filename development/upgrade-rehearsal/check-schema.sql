-- Checks that the upgraded database has every unique index and every NOT NULL column that the
-- newer build creates on an empty database. Run it in the upgraded database, with the sqlcmd
-- variable REFERENCE_DB naming the database the newer build created from scratch.
--
-- Output: one MISSING|... line for each unique key or NOT NULL column the upgraded database lacks,
-- or a single OK|... line. It ends with an error when anything is missing, so sqlcmd -b exits 1.

SET NOCOUNT ON;

IF OBJECT_ID('tempdb..#unique_keys') IS NOT NULL DROP TABLE #unique_keys;
CREATE TABLE #unique_keys (db sysname, tbl sysname, cols nvarchar(4000));

INSERT INTO #unique_keys (db, tbl, cols)
SELECT 'reference', t.name, STRING_AGG(CAST(c.name AS nvarchar(4000)), ',') WITHIN GROUP (ORDER BY c.name)
FROM [$(REFERENCE_DB)].sys.indexes i
JOIN [$(REFERENCE_DB)].sys.tables t ON t.object_id = i.object_id
JOIN [$(REFERENCE_DB)].sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.is_included_column = 0
JOIN [$(REFERENCE_DB)].sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
WHERE i.is_unique = 1 AND i.is_primary_key = 0
GROUP BY t.name, i.index_id;

INSERT INTO #unique_keys (db, tbl, cols)
SELECT 'upgrade', t.name, STRING_AGG(CAST(c.name AS nvarchar(4000)), ',') WITHIN GROUP (ORDER BY c.name)
FROM sys.indexes i
JOIN sys.tables t ON t.object_id = i.object_id
JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.is_included_column = 0
JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
WHERE i.is_unique = 1 AND i.is_primary_key = 0
GROUP BY t.name, i.index_id;

DECLARE @missing TABLE (line nvarchar(4000));

INSERT INTO @missing (line)
SELECT 'MISSING|unique index|' + r.tbl + '|' + r.cols
FROM #unique_keys r
WHERE r.db = 'reference'
  AND NOT EXISTS (SELECT 1 FROM #unique_keys u WHERE u.db = 'upgrade' AND u.tbl = r.tbl AND u.cols = r.cols);

INSERT INTO @missing (line)
SELECT 'MISSING|NOT NULL column|' + t.name + '|' + c.name
FROM [$(REFERENCE_DB)].sys.columns c
JOIN [$(REFERENCE_DB)].sys.tables t ON t.object_id = c.object_id
WHERE c.is_nullable = 0 AND c.is_identity = 0
  AND NOT EXISTS (
    SELECT 1 FROM sys.columns oc JOIN sys.tables ot ON ot.object_id = oc.object_id
    WHERE ot.name = t.name AND oc.name = c.name AND oc.is_nullable = 0);

SELECT line FROM @missing ORDER BY line;

IF EXISTS (SELECT 1 FROM @missing)
  RAISERROR('The upgraded database lacks unique indexes or NOT NULL columns of the newer build.', 16, 1);
ELSE
  SELECT 'OK|every unique index and NOT NULL column of the newer build is in the upgraded database|'
         + CAST((SELECT COUNT(*) FROM #unique_keys WHERE db = 'reference') AS varchar(10)) + ' unique indexes';
