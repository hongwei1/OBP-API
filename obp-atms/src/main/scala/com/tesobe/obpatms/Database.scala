package com.tesobe.obpatms

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}

import java.sql.Connection

/**
  * Column names mirror code.atms.MappedAtm / code.branches.MappedBranches in obp-api one for
  * one (snake_case of the same mField names) — this table is meant to be recognisable to anyone
  * who already knows the mapped schema it replaces, not a redesign. List-valued fields
  * (supportedLanguages, services, ...) are stored the same way the mapped table stores them:
  * a comma-joined string, not a real array column, so the read/write code needs no format
  * migration if a deployment ever needs to compare the two schemas side by side.
  */
object Schema {

  val createAtms: String =
    """
    |CREATE TABLE IF NOT EXISTS atms (
    |  bank_id VARCHAR(255) NOT NULL,
    |  atm_id VARCHAR(255) NOT NULL,
    |  name VARCHAR(255) NOT NULL DEFAULT '',
    |  line1 VARCHAR(255) NOT NULL DEFAULT '',
    |  line2 VARCHAR(255) NOT NULL DEFAULT '',
    |  line3 VARCHAR(255) NOT NULL DEFAULT '',
    |  city VARCHAR(255) NOT NULL DEFAULT '',
    |  county VARCHAR(255),
    |  state VARCHAR(255) NOT NULL DEFAULT '',
    |  country_code VARCHAR(2) NOT NULL DEFAULT '',
    |  post_code VARCHAR(20) NOT NULL DEFAULT '',
    |  location_latitude DOUBLE PRECISION NOT NULL DEFAULT 0,
    |  location_longitude DOUBLE PRECISION NOT NULL DEFAULT 0,
    |  license_id VARCHAR(255) NOT NULL DEFAULT '',
    |  license_name VARCHAR(255) NOT NULL DEFAULT '',
    |  opening_time_on_monday VARCHAR(5) NOT NULL DEFAULT '',
    |  closing_time_on_monday VARCHAR(5) NOT NULL DEFAULT '',
    |  opening_time_on_tuesday VARCHAR(5) NOT NULL DEFAULT '',
    |  closing_time_on_tuesday VARCHAR(5) NOT NULL DEFAULT '',
    |  opening_time_on_wednesday VARCHAR(5) NOT NULL DEFAULT '',
    |  closing_time_on_wednesday VARCHAR(5) NOT NULL DEFAULT '',
    |  opening_time_on_thursday VARCHAR(5) NOT NULL DEFAULT '',
    |  closing_time_on_thursday VARCHAR(5) NOT NULL DEFAULT '',
    |  opening_time_on_friday VARCHAR(5) NOT NULL DEFAULT '',
    |  closing_time_on_friday VARCHAR(5) NOT NULL DEFAULT '',
    |  opening_time_on_saturday VARCHAR(5) NOT NULL DEFAULT '',
    |  closing_time_on_saturday VARCHAR(5) NOT NULL DEFAULT '',
    |  opening_time_on_sunday VARCHAR(5) NOT NULL DEFAULT '',
    |  closing_time_on_sunday VARCHAR(5) NOT NULL DEFAULT '',
    |  is_accessible VARCHAR(1) NOT NULL DEFAULT '',
    |  located_at VARCHAR(32) NOT NULL DEFAULT '',
    |  more_info VARCHAR(128) NOT NULL DEFAULT '',
    |  has_deposit_capability VARCHAR(1) NOT NULL DEFAULT '',
    |  supported_languages TEXT NOT NULL DEFAULT '',
    |  services TEXT NOT NULL DEFAULT '',
    |  notes TEXT NOT NULL DEFAULT '',
    |  accessibility_features TEXT NOT NULL DEFAULT '',
    |  supported_currencies TEXT NOT NULL DEFAULT '',
    |  location_categories TEXT NOT NULL DEFAULT '',
    |  minimum_withdrawal VARCHAR(255) NOT NULL DEFAULT '',
    |  branch_identification VARCHAR(255) NOT NULL DEFAULT '',
    |  site_identification VARCHAR(255) NOT NULL DEFAULT '',
    |  site_name VARCHAR(255) NOT NULL DEFAULT '',
    |  cash_withdrawal_national_fee VARCHAR(255) NOT NULL DEFAULT '',
    |  cash_withdrawal_international_fee VARCHAR(255) NOT NULL DEFAULT '',
    |  balance_inquiry_fee VARCHAR(255) NOT NULL DEFAULT '',
    |  atm_type VARCHAR(255) NOT NULL DEFAULT '',
    |  phone VARCHAR(255) NOT NULL DEFAULT '',
    |  PRIMARY KEY (bank_id, atm_id)
    |)
    """.stripMargin

  val createBranches: String =
    """
    |CREATE TABLE IF NOT EXISTS branches (
    |  bank_id VARCHAR(255) NOT NULL,
    |  branch_id VARCHAR(255) NOT NULL,
    |  name VARCHAR(255) NOT NULL DEFAULT '',
    |  line1 VARCHAR(255) NOT NULL DEFAULT '',
    |  line2 VARCHAR(255) NOT NULL DEFAULT '',
    |  line3 VARCHAR(255) NOT NULL DEFAULT '',
    |  city VARCHAR(255) NOT NULL DEFAULT '',
    |  county VARCHAR(255),
    |  state VARCHAR(255) NOT NULL DEFAULT '',
    |  country_code VARCHAR(2) NOT NULL DEFAULT '',
    |  post_code VARCHAR(20) NOT NULL DEFAULT '',
    |  location_latitude DOUBLE PRECISION NOT NULL DEFAULT 0,
    |  location_longitude DOUBLE PRECISION NOT NULL DEFAULT 0,
    |  license_id VARCHAR(255) NOT NULL DEFAULT '',
    |  license_name VARCHAR(255) NOT NULL DEFAULT '',
    |  lobby_hours VARCHAR(2000) NOT NULL DEFAULT '',
    |  drive_up_hours VARCHAR(2000) NOT NULL DEFAULT '',
    |  branch_routing_scheme VARCHAR(32) NOT NULL DEFAULT '',
    |  branch_routing_address VARCHAR(64) NOT NULL DEFAULT '',
    |  lobby_opening_time_on_monday VARCHAR(5) NOT NULL DEFAULT '',
    |  lobby_closing_time_on_monday VARCHAR(5) NOT NULL DEFAULT '',
    |  lobby_opening_time_on_tuesday VARCHAR(5) NOT NULL DEFAULT '',
    |  lobby_closing_time_on_tuesday VARCHAR(5) NOT NULL DEFAULT '',
    |  lobby_opening_time_on_wednesday VARCHAR(5) NOT NULL DEFAULT '',
    |  lobby_closing_time_on_wednesday VARCHAR(5) NOT NULL DEFAULT '',
    |  lobby_opening_time_on_thursday VARCHAR(5) NOT NULL DEFAULT '',
    |  lobby_closing_time_on_thursday VARCHAR(5) NOT NULL DEFAULT '',
    |  lobby_opening_time_on_friday VARCHAR(5) NOT NULL DEFAULT '',
    |  lobby_closing_time_on_friday VARCHAR(5) NOT NULL DEFAULT '',
    |  lobby_opening_time_on_saturday VARCHAR(5) NOT NULL DEFAULT '',
    |  lobby_closing_time_on_saturday VARCHAR(5) NOT NULL DEFAULT '',
    |  lobby_opening_time_on_sunday VARCHAR(5) NOT NULL DEFAULT '',
    |  lobby_closing_time_on_sunday VARCHAR(5) NOT NULL DEFAULT '',
    |  drive_up_opening_time_on_monday VARCHAR(5) NOT NULL DEFAULT '',
    |  drive_up_closing_time_on_monday VARCHAR(5) NOT NULL DEFAULT '',
    |  drive_up_opening_time_on_tuesday VARCHAR(5) NOT NULL DEFAULT '',
    |  drive_up_closing_time_on_tuesday VARCHAR(5) NOT NULL DEFAULT '',
    |  drive_up_opening_time_on_wednesday VARCHAR(5) NOT NULL DEFAULT '',
    |  drive_up_closing_time_on_wednesday VARCHAR(5) NOT NULL DEFAULT '',
    |  drive_up_opening_time_on_thursday VARCHAR(5) NOT NULL DEFAULT '',
    |  drive_up_closing_time_on_thursday VARCHAR(5) NOT NULL DEFAULT '',
    |  drive_up_opening_time_on_friday VARCHAR(5) NOT NULL DEFAULT '',
    |  drive_up_closing_time_on_friday VARCHAR(5) NOT NULL DEFAULT '',
    |  drive_up_opening_time_on_saturday VARCHAR(5) NOT NULL DEFAULT '',
    |  drive_up_closing_time_on_saturday VARCHAR(5) NOT NULL DEFAULT '',
    |  drive_up_opening_time_on_sunday VARCHAR(5) NOT NULL DEFAULT '',
    |  drive_up_closing_time_on_sunday VARCHAR(5) NOT NULL DEFAULT '',
    |  is_accessible VARCHAR(1) NOT NULL DEFAULT '',
    |  accessible_features VARCHAR(250) NOT NULL DEFAULT '',
    |  branch_type VARCHAR(32) NOT NULL DEFAULT '',
    |  more_info VARCHAR(128) NOT NULL DEFAULT '',
    |  phone_number VARCHAR(32) NOT NULL DEFAULT '',
    |  is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    |  PRIMARY KEY (bank_id, branch_id)
    |)
    """.stripMargin
}

/** Thin HikariCP wrapper. No ORM: the repos below write plain SQL against these two tables. */
class Database(config: DbConfig) {

  private val dataSource: HikariDataSource = {
    val hikariConfig = new HikariConfig()
    hikariConfig.setJdbcUrl(config.jdbcUrl)
    hikariConfig.setUsername(config.username)
    hikariConfig.setPassword(config.password)
    hikariConfig.setMaximumPoolSize(config.maxPoolSize)
    hikariConfig.setPoolName("obp-atms")
    new HikariDataSource(hikariConfig)
  }

  def withConnection[A](f: Connection => A): A = {
    val conn = dataSource.getConnection()
    try f(conn)
    finally conn.close()
  }

  def migrate(): Unit = withConnection { conn =>
    val stmt = conn.createStatement()
    try {
      stmt.execute(Schema.createAtms)
      stmt.execute(Schema.createBranches)
    } finally stmt.close()
  }

  def isHealthy: Boolean =
    try withConnection { conn => conn.isValid(2) }
    catch { case _: Throwable => false }

  def close(): Unit = dataSource.close()
}
