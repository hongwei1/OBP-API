-- Adds rows that the newer build's schema cannot take as they are. Run it in the database the
-- older build created, with the sqlcmd variable REFERENCE_DB naming a database the newer build
-- created from scratch.
--
-- It compares the two schemas and seeds two kinds of table:
--
--   * A table that exists in the older schema and gets a unique index there that it does not
--     already have. Two rows are added with the same values in the index columns that already
--     exist. A column the newer build adds is NULL in both rows, which SQL Server's unique index
--     also counts as a duplicate. Where those columns can be NULL, two more rows are added with
--     NULL in all of them.
--   * A table that exists in the older schema and gets a NOT NULL column it does not have, or
--     a column that becomes NOT NULL. Two rows are added, which have no value for that column.
--
-- Every other column is left NULL, except a column that is NOT NULL or belongs to another unique
-- index, which gets a value of its type. Each pair is inserted in its own transaction. When the
-- older schema already refuses the duplicate, the pair is rolled back and reported as SKIPPED.
--
-- Output lines, one per table and key:
--   CANDIDATE|<table>|<columns>|<reason>
--   SEEDED|<table>|<columns>|<what was inserted>
--   SKIPPED|<table>|<columns>|<why>

SET NOCOUNT ON;

IF OBJECT_ID('tempdb..#unique_keys') IS NOT NULL DROP TABLE #unique_keys;
IF OBJECT_ID('tempdb..#candidates') IS NOT NULL DROP TABLE #candidates;

-- Unique keys (not primary keys) of both schemas: table, and its columns sorted by name.
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

CREATE TABLE #candidates (id int IDENTITY, tbl sysname, cols nvarchar(4000), reason nvarchar(400));

-- New unique keys on tables the older schema has.
INSERT INTO #candidates (tbl, cols, reason)
SELECT r.tbl, r.cols, 'new unique index'
FROM #unique_keys r
WHERE r.db = 'reference'
  AND EXISTS (SELECT 1 FROM sys.tables t WHERE t.name = r.tbl)
  AND NOT EXISTS (SELECT 1 FROM #unique_keys u WHERE u.db = 'upgrade' AND u.tbl = r.tbl AND u.cols = r.cols);

-- New NOT NULL columns, or columns that become NOT NULL, on tables the older schema has.
INSERT INTO #candidates (tbl, cols, reason)
SELECT t.name, '', 'NOT NULL column ' + c.name
FROM [$(REFERENCE_DB)].sys.columns c
JOIN [$(REFERENCE_DB)].sys.tables t ON t.object_id = c.object_id
WHERE c.is_nullable = 0 AND c.is_identity = 0
  AND EXISTS (SELECT 1 FROM sys.tables ot WHERE ot.name = t.name)
  AND NOT EXISTS (
    SELECT 1 FROM sys.columns oc JOIN sys.tables ot ON ot.object_id = oc.object_id
    WHERE ot.name = t.name AND oc.name = c.name AND oc.is_nullable = 0);

SELECT 'CANDIDATE|' + tbl + '|' + cols + '|' + reason FROM #candidates ORDER BY tbl, cols;

-- Seed each candidate.
DECLARE @id int = 0, @tbl sysname, @cols nvarchar(4000), @reason nvarchar(400);
DECLARE @columns nvarchar(max), @values nvarchar(max), @null_values nvarchar(max), @sql nvarchar(max);
DECLARE @key_count int, @nullable_key_count int, @unsupported nvarchar(max);

WHILE 1 = 1
BEGIN
  SELECT TOP 1 @id = id, @tbl = tbl, @cols = cols, @reason = reason FROM #candidates WHERE id > @id ORDER BY id;
  IF @@ROWCOUNT = 0 BREAK;

  -- Columns of the table in the older schema, with the value each row gets.
  ;WITH cols AS (
    SELECT c.name, ty.name AS type_name, c.max_length, c.is_nullable,
           CASE WHEN ',' + @cols + ',' LIKE '%,' + c.name + ',%' THEN 1 ELSE 0 END AS is_key,
           CASE WHEN EXISTS (
             SELECT 1 FROM sys.indexes i
             JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
             WHERE i.object_id = c.object_id AND i.is_unique = 1 AND i.is_primary_key = 0 AND ic.column_id = c.column_id
           ) THEN 1 ELSE 0 END AS in_unique_index
    FROM sys.columns c
    JOIN sys.tables t ON t.object_id = c.object_id
    JOIN sys.types ty ON ty.user_type_id = c.user_type_id
    WHERE t.name = @tbl AND c.is_identity = 0 AND c.is_computed = 0
  ), valued AS (
    SELECT name, is_key, is_nullable,
      CASE
        -- The same value in both rows: the key columns, and NOT NULL columns of no other unique index.
        WHEN is_key = 1 OR (is_nullable = 0 AND in_unique_index = 0) THEN
          CASE
            WHEN type_name IN ('char','varchar','text') THEN 'LEFT(''rehearsal'', ' + CAST(CASE WHEN max_length = -1 OR type_name = 'text' THEN 9 ELSE max_length END AS varchar(10)) + ')'
            WHEN type_name IN ('nchar','nvarchar','ntext') THEN 'LEFT(N''rehearsal'', ' + CAST(CASE WHEN max_length = -1 OR type_name = 'ntext' THEN 9 ELSE max_length / 2 END AS varchar(10)) + ')'
            WHEN type_name IN ('bigint','int','smallint','tinyint','decimal','numeric','float','real','money','smallmoney','bit') THEN '1'
            WHEN type_name IN ('date','datetime','datetime2','smalldatetime','datetimeoffset') THEN '''2020-01-01'''
            WHEN type_name = 'uniqueidentifier' THEN '''00000000-0000-0000-0000-000000000001'''
            WHEN type_name IN ('binary','varbinary','image') THEN '0x01'
          END
        -- A different value in each row: columns of another unique index, so they do not collide.
        WHEN in_unique_index = 1 THEN
          CASE
            WHEN type_name IN ('char','varchar','nchar','nvarchar') THEN 'LEFT(REPLACE(CONVERT(varchar(36), NEWID()), ''-'', ''''), ' + CAST(CASE WHEN max_length = -1 THEN 32 WHEN type_name LIKE 'n%' THEN max_length / 2 ELSE max_length END AS varchar(10)) + ')'
            WHEN type_name IN ('bigint','int') THEN 'ABS(CHECKSUM(NEWID())) % 1000000000'
            WHEN type_name IN ('date','datetime','datetime2','smalldatetime','datetimeoffset') THEN 'DATEADD(second, ABS(CHECKSUM(NEWID())) % 100000000, ''2000-01-01'')'
            WHEN type_name = 'uniqueidentifier' THEN 'NEWID()'
          END
      END AS value_sql,
      type_name
    FROM cols
  )
  SELECT
    @columns = STRING_AGG(CAST(QUOTENAME(name) AS nvarchar(max)), ', ') WITHIN GROUP (ORDER BY name),
    @values = STRING_AGG(CAST(value_sql AS nvarchar(max)), ', ') WITHIN GROUP (ORDER BY name),
    @null_values = STRING_AGG(CAST(CASE WHEN is_key = 1 THEN 'NULL' ELSE value_sql END AS nvarchar(max)), ', ') WITHIN GROUP (ORDER BY name),
    @key_count = SUM(is_key),
    @nullable_key_count = SUM(CASE WHEN is_key = 1 AND is_nullable = 1 THEN 1 ELSE 0 END),
    @unsupported = STRING_AGG(CASE WHEN value_sql IS NULL AND (is_key = 1 OR is_nullable = 0) THEN name + ' ' + type_name END, ', ')
  FROM valued
  WHERE value_sql IS NOT NULL OR is_key = 1 OR is_nullable = 0;

  IF @unsupported IS NOT NULL
  BEGIN
    SELECT 'SKIPPED|' + @tbl + '|' + @cols + '|no value for column(s) ' + @unsupported;
    CONTINUE;
  END;

  -- A table with no column to fill: insert default values.
  IF @columns IS NULL
    SET @sql = N'INSERT INTO ' + QUOTENAME(@tbl) + N' DEFAULT VALUES; INSERT INTO ' + QUOTENAME(@tbl) + N' DEFAULT VALUES;';
  ELSE
    SET @sql = N'INSERT INTO ' + QUOTENAME(@tbl) + N' (' + @columns + N') VALUES (' + @values + N'); '
             + N'INSERT INTO ' + QUOTENAME(@tbl) + N' (' + @columns + N') VALUES (' + @values + N');';

  BEGIN TRY
    BEGIN TRANSACTION;
    EXEC sp_executesql @sql;
    COMMIT;
    SELECT 'SEEDED|' + @tbl + '|' + @cols + '|2 rows with the same key' + CASE WHEN @key_count = 0 THEN ' (key columns are new, so NULL)' ELSE '' END;
  END TRY
  BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK;
    SELECT 'SKIPPED|' + @tbl + '|' + @cols + '|the older schema refuses it: ' + ERROR_MESSAGE();
  END CATCH;

  -- The same with the key columns NULL, where they can be.
  IF @key_count > 0 AND @nullable_key_count = @key_count
  BEGIN
    SET @sql = N'INSERT INTO ' + QUOTENAME(@tbl) + N' (' + @columns + N') VALUES (' + @null_values + N'); '
             + N'INSERT INTO ' + QUOTENAME(@tbl) + N' (' + @columns + N') VALUES (' + @null_values + N');';
    BEGIN TRY
      BEGIN TRANSACTION;
      EXEC sp_executesql @sql;
      COMMIT;
      SELECT 'SEEDED|' + @tbl + '|' + @cols + '|2 rows with NULL in every key column';
    END TRY
    BEGIN CATCH
      IF @@TRANCOUNT > 0 ROLLBACK;
      SELECT 'SKIPPED|' + @tbl + '|' + @cols + '|NULL keys: the older schema refuses it: ' + ERROR_MESSAGE();
    END CATCH;
  END;
END;
