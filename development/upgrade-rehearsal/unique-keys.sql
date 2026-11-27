-- Collects the unique keys (not primary keys) of both schemas into #unique_keys: the database
-- ('reference' or 'upgrade'), the table, and the key's columns sorted by name. seed-dirty-data.sql
-- and check-schema.sql include it with sqlcmd's :r command. REFERENCE_DB names the database the
-- newer build created from scratch; the current database is the upgraded one.

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
