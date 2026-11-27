-- Rows for migrations that rewrite values already stored, which the schema comparison in
-- seed-dirty-data.sql cannot find: a migration that renames a value can make two rows the same
-- under a unique index that already exists, and nothing in the two schemas says so. Each case
-- below names the migration it is for and writes the rows that migration has to cope with.
-- Add a case here when a migration rewrites a column that is part of a unique key.
--
-- Run in the database the older build created, after seed-dirty-data.sql.
-- Output lines: SEEDED|<case>|<what was inserted>, or SKIPPED|<case>|<why>.

SET NOCOUNT ON;

-- Case: Migration.database.renameDynamicEntityRoles and renameDynamicEntityDefinitionRoles
-- (MigrationOfDynamicEntityRoleNames). They rename stored Dynamic Entity Roles and move system
-- level grants from the empty bank id to SYS. Several old names can become one new name, and the
-- unique index on (mbankid, muserid, mrolename) then holds the first renamed row against the next:
--   * a System twin and its plain Role at the empty bank id (both become ..Record_X at SYS);
--   * the System and BankLevel variant of a Definition Role at the empty bank id;
--   * a grant whose new name is already stored, as a run that stopped part way leaves it.
IF OBJECT_ID('mappedentitlement', 'U') IS NULL
  SELECT 'SKIPPED|dynamic entity role rename|no mappedentitlement table';
ELSE
BEGIN
  DECLARE @user nvarchar(255) = N'rehearsal-role-rename-user';
  BEGIN TRY
    INSERT INTO mappedentitlement (mentitlementid, mbankid, muserid, mrolename)
    VALUES
      (CONVERT(nvarchar(36), NEWID()), N'',      @user, N'CanGetDynamicEntity_SystemRehearsal'),
      (CONVERT(nvarchar(36), NEWID()), N'',      @user, N'CanGetDynamicEntity_Rehearsal'),
      (CONVERT(nvarchar(36), NEWID()), N'',      @user, N'CanCreateSystemLevelDynamicEntity'),
      (CONVERT(nvarchar(36), NEWID()), N'',      @user, N'CanCreateBankLevelDynamicEntity'),
      (CONVERT(nvarchar(36), NEWID()), N'SYS',   @user, N'CanDeleteDynamicEntityRecord_Rehearsal'),
      (CONVERT(nvarchar(36), NEWID()), N'',      @user, N'CanDeleteDynamicEntity_SystemRehearsal'),
      (CONVERT(nvarchar(36), NEWID()), N'bank1', @user, N'CanUpdateDynamicEntityDefinition'),
      (CONVERT(nvarchar(36), NEWID()), N'bank1', @user, N'CanUpdateBankLevelDynamicEntity');
    SELECT 'SEEDED|dynamic entity role rename|8 entitlements whose new names meet';
  END TRY
  BEGIN CATCH
    SELECT 'SKIPPED|dynamic entity role rename|the older schema refuses it: ' + ERROR_MESSAGE();
  END CATCH;
END;
