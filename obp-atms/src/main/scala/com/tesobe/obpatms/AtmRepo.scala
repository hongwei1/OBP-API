package com.tesobe.obpatms

import com.openbankproject.commons.model._

import java.sql.{Connection, ResultSet}

/**
  * Owns the `atms` table. Every method takes/returns the same AtmT/AtmTCommons shapes the core
  * uses, so the message handler never has to know about columns — only this file does.
  *
  * List-valued fields are comma-joined on write and split on read, exactly like
  * code.atms.MappedAtm (mServices, mNotes, ...) in obp-api: this table is a drop-in replacement
  * for that one, not a redesign, and a deployment comparing the two should see the same
  * on-disk representation.
  */
class AtmRepo(db: Database) {

  private def csv(values: Option[List[String]]): String = values.map(_.mkString(",")).getOrElse("")
  private def uncsv(s: String): Option[List[String]] = if (s == null || s.isEmpty) None else Some(s.split(",").toList)
  private def blank(s: Option[String]): String = s.getOrElse("")
  private def unblank(s: String): Option[String] = if (s == null || s.isEmpty) None else Some(s)
  private def flag(b: Option[Boolean]): String = b match {
    case Some(true) => "Y"
    case Some(false) => "N"
    case None => ""
  }
  private def unflag(s: String): Option[Boolean] = s match {
    case "Y" => Some(true)
    case "N" => Some(false)
    case _ => None
  }

  private def fromRow(rs: ResultSet): AtmTCommons = AtmTCommons(
    atmId = AtmId(rs.getString("atm_id")),
    bankId = BankId(rs.getString("bank_id")),
    name = rs.getString("name"),
    address = Address(
      line1 = rs.getString("line1"), line2 = rs.getString("line2"), line3 = rs.getString("line3"),
      city = rs.getString("city"), county = unblank(rs.getString("county")), state = rs.getString("state"),
      countryCode = rs.getString("country_code"), postCode = rs.getString("post_code")
    ),
    location = Location(rs.getDouble("location_latitude"), rs.getDouble("location_longitude"), None, None),
    meta = Meta(License(rs.getString("license_id"), rs.getString("license_name"))),
    OpeningTimeOnMonday = unblank(rs.getString("opening_time_on_monday")), ClosingTimeOnMonday = unblank(rs.getString("closing_time_on_monday")),
    OpeningTimeOnTuesday = unblank(rs.getString("opening_time_on_tuesday")), ClosingTimeOnTuesday = unblank(rs.getString("closing_time_on_tuesday")),
    OpeningTimeOnWednesday = unblank(rs.getString("opening_time_on_wednesday")), ClosingTimeOnWednesday = unblank(rs.getString("closing_time_on_wednesday")),
    OpeningTimeOnThursday = unblank(rs.getString("opening_time_on_thursday")), ClosingTimeOnThursday = unblank(rs.getString("closing_time_on_thursday")),
    OpeningTimeOnFriday = unblank(rs.getString("opening_time_on_friday")), ClosingTimeOnFriday = unblank(rs.getString("closing_time_on_friday")),
    OpeningTimeOnSaturday = unblank(rs.getString("opening_time_on_saturday")), ClosingTimeOnSaturday = unblank(rs.getString("closing_time_on_saturday")),
    OpeningTimeOnSunday = unblank(rs.getString("opening_time_on_sunday")), ClosingTimeOnSunday = unblank(rs.getString("closing_time_on_sunday")),
    isAccessible = unflag(rs.getString("is_accessible")),
    locatedAt = unblank(rs.getString("located_at")),
    moreInfo = unblank(rs.getString("more_info")),
    hasDepositCapability = unflag(rs.getString("has_deposit_capability")),
    supportedLanguages = uncsv(rs.getString("supported_languages")),
    services = uncsv(rs.getString("services")),
    accessibilityFeatures = uncsv(rs.getString("accessibility_features")),
    supportedCurrencies = uncsv(rs.getString("supported_currencies")),
    notes = uncsv(rs.getString("notes")),
    locationCategories = uncsv(rs.getString("location_categories")),
    minimumWithdrawal = unblank(rs.getString("minimum_withdrawal")),
    branchIdentification = unblank(rs.getString("branch_identification")),
    siteIdentification = unblank(rs.getString("site_identification")),
    siteName = unblank(rs.getString("site_name")),
    cashWithdrawalNationalFee = unblank(rs.getString("cash_withdrawal_national_fee")),
    cashWithdrawalInternationalFee = unblank(rs.getString("cash_withdrawal_international_fee")),
    balanceInquiryFee = unblank(rs.getString("balance_inquiry_fee")),
    atmType = unblank(rs.getString("atm_type")),
    phone = unblank(rs.getString("phone"))
  )

  // createOrUpdateAtm is a full-row replace, not a partial merge, so DELETE-then-INSERT inside
  // one connection (Database.withConnection hands back a single JDBC Connection, so both
  // statements share it — no separate transaction wiring needed) gives upsert semantics without
  // relying on `INSERT ... ON CONFLICT`, which H2 2.2.220 does not accept even in
  // MODE=PostgreSQL (verified directly against the driver, not assumed from the mode name) —
  // this repo is exercised against H2 in AtmRepoSpec and Postgres in the deployed service, and
  // both need to accept the same SQL.
  def upsert(atm: AtmT): AtmTCommons = db.withConnection { conn =>
    val deleteExisting = conn.prepareStatement("DELETE FROM atms WHERE bank_id = ? AND atm_id = ?")
    try {
      deleteExisting.setString(1, atm.bankId.value)
      deleteExisting.setString(2, atm.atmId.value)
      deleteExisting.executeUpdate()
    } finally deleteExisting.close()

    val sql =
      """
      |INSERT INTO atms (bank_id, atm_id, name, line1, line2, line3, city, county, state, country_code, post_code,
      |  location_latitude, location_longitude, license_id, license_name,
      |  opening_time_on_monday, closing_time_on_monday, opening_time_on_tuesday, closing_time_on_tuesday,
      |  opening_time_on_wednesday, closing_time_on_wednesday, opening_time_on_thursday, closing_time_on_thursday,
      |  opening_time_on_friday, closing_time_on_friday, opening_time_on_saturday, closing_time_on_saturday,
      |  opening_time_on_sunday, closing_time_on_sunday, is_accessible, located_at, more_info, has_deposit_capability,
      |  supported_languages, services, accessibility_features, supported_currencies, notes, location_categories,
      |  minimum_withdrawal, branch_identification, site_identification, site_name,
      |  cash_withdrawal_national_fee, cash_withdrawal_international_fee, balance_inquiry_fee, atm_type, phone)
      |VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      """.stripMargin
    val ps = conn.prepareStatement(sql)
    try {
      var i = 0
      def next(): Int = { i += 1; i }
      ps.setString(next(), atm.bankId.value)
      ps.setString(next(), atm.atmId.value)
      ps.setString(next(), atm.name)
      ps.setString(next(), atm.address.line1)
      ps.setString(next(), atm.address.line2)
      ps.setString(next(), atm.address.line3)
      ps.setString(next(), atm.address.city)
      ps.setString(next(), blank(atm.address.county))
      ps.setString(next(), atm.address.state)
      ps.setString(next(), atm.address.countryCode)
      ps.setString(next(), atm.address.postCode)
      ps.setDouble(next(), atm.location.latitude)
      ps.setDouble(next(), atm.location.longitude)
      ps.setString(next(), atm.meta.license.id)
      ps.setString(next(), atm.meta.license.name)
      ps.setString(next(), blank(atm.OpeningTimeOnMonday)); ps.setString(next(), blank(atm.ClosingTimeOnMonday))
      ps.setString(next(), blank(atm.OpeningTimeOnTuesday)); ps.setString(next(), blank(atm.ClosingTimeOnTuesday))
      ps.setString(next(), blank(atm.OpeningTimeOnWednesday)); ps.setString(next(), blank(atm.ClosingTimeOnWednesday))
      ps.setString(next(), blank(atm.OpeningTimeOnThursday)); ps.setString(next(), blank(atm.ClosingTimeOnThursday))
      ps.setString(next(), blank(atm.OpeningTimeOnFriday)); ps.setString(next(), blank(atm.ClosingTimeOnFriday))
      ps.setString(next(), blank(atm.OpeningTimeOnSaturday)); ps.setString(next(), blank(atm.ClosingTimeOnSaturday))
      ps.setString(next(), blank(atm.OpeningTimeOnSunday)); ps.setString(next(), blank(atm.ClosingTimeOnSunday))
      ps.setString(next(), flag(atm.isAccessible))
      ps.setString(next(), blank(atm.locatedAt))
      ps.setString(next(), blank(atm.moreInfo))
      ps.setString(next(), flag(atm.hasDepositCapability))
      ps.setString(next(), csv(atm.supportedLanguages))
      ps.setString(next(), csv(atm.services))
      ps.setString(next(), csv(atm.accessibilityFeatures))
      ps.setString(next(), csv(atm.supportedCurrencies))
      ps.setString(next(), csv(atm.notes))
      ps.setString(next(), csv(atm.locationCategories))
      ps.setString(next(), blank(atm.minimumWithdrawal))
      ps.setString(next(), blank(atm.branchIdentification))
      ps.setString(next(), blank(atm.siteIdentification))
      ps.setString(next(), blank(atm.siteName))
      ps.setString(next(), blank(atm.cashWithdrawalNationalFee))
      ps.setString(next(), blank(atm.cashWithdrawalInternationalFee))
      ps.setString(next(), blank(atm.balanceInquiryFee))
      ps.setString(next(), blank(atm.atmType))
      ps.setString(next(), blank(atm.phone))
      ps.executeUpdate()
    } finally ps.close()
    findOne(atm.bankId, atm.atmId).getOrElse(
      throw new IllegalStateException(s"upsert of atm ${atm.bankId.value}/${atm.atmId.value} did not produce a row"))
  }

  def findOne(bankId: BankId, atmId: AtmId): Option[AtmTCommons] = db.withConnection { conn =>
    val ps = conn.prepareStatement("SELECT * FROM atms WHERE bank_id = ? AND atm_id = ?")
    try {
      ps.setString(1, bankId.value)
      ps.setString(2, atmId.value)
      val rs = ps.executeQuery()
      try if (rs.next()) Some(fromRow(rs)) else None
      finally rs.close()
    } finally ps.close()
  }

  /** Mirrors code.bankconnectors.LocalMappedConnector.getAtms: limit/offset/date-range are
    * accepted by the wire protocol but the mapped connector this replaces ignores them too
    * (MappedAtm.findAll(By(mBankId, ...)) with no further filter) — matching that, not adding
    * pagination the core never asked this connector method to honour. */
  def findByBank(bankId: BankId): List[AtmTCommons] = db.withConnection { conn =>
    val ps = conn.prepareStatement("SELECT * FROM atms WHERE bank_id = ? ORDER BY atm_id")
    try {
      ps.setString(1, bankId.value)
      val rs = ps.executeQuery()
      try Iterator.continually(rs).takeWhile(_.next()).map(fromRow).toList
      finally rs.close()
    } finally ps.close()
  }

  def delete(bankId: BankId, atmId: AtmId): Boolean = db.withConnection { conn =>
    val ps = conn.prepareStatement("DELETE FROM atms WHERE bank_id = ? AND atm_id = ?")
    try {
      ps.setString(1, bankId.value)
      ps.setString(2, atmId.value)
      ps.executeUpdate() > 0
    } finally ps.close()
  }

  private def updateListColumn(column: String, bankId: BankId, atmId: AtmId, values: List[String]): Option[AtmTCommons] =
    db.withConnection { conn =>
      val ps = conn.prepareStatement(s"UPDATE atms SET $column = ? WHERE bank_id = ? AND atm_id = ?")
      try {
        ps.setString(1, values.mkString(","))
        ps.setString(2, bankId.value)
        ps.setString(3, atmId.value)
        ps.executeUpdate()
      } finally ps.close()
      findOne(bankId, atmId)
    }

  def updateAccessibilityFeatures(bankId: BankId, atmId: AtmId, values: List[String]): Option[AtmTCommons] =
    updateListColumn("accessibility_features", bankId, atmId, values)

  def updateLocationCategories(bankId: BankId, atmId: AtmId, values: List[String]): Option[AtmTCommons] =
    updateListColumn("location_categories", bankId, atmId, values)

  def updateNotes(bankId: BankId, atmId: AtmId, values: List[String]): Option[AtmTCommons] =
    updateListColumn("notes", bankId, atmId, values)

  // OutBoundUpdateAtmServices carries its payload in a field literally named `supportedCurrencies`
  // (obp-commons dto/JsonsTransfer.scala) even though Connector.updateAtmServices documents it as
  // the services list, and LocalMappedConnector.updateAtmServices writes it into mServices, not
  // mSupportedCurrencies. That mismatch is pre-existing upstream of this module (the trait method's
  // own parameter is misnamed the same way) — this repo mirrors LocalMappedConnector's actual
  // behaviour (write to `services`), not the misleading field name on the wire.
  def updateServices(bankId: BankId, atmId: AtmId, values: List[String]): Option[AtmTCommons] =
    updateListColumn("services", bankId, atmId, values)

  def updateSupportedCurrencies(bankId: BankId, atmId: AtmId, values: List[String]): Option[AtmTCommons] =
    updateListColumn("supported_currencies", bankId, atmId, values)

  def updateSupportedLanguages(bankId: BankId, atmId: AtmId, values: List[String]): Option[AtmTCommons] =
    updateListColumn("supported_languages", bankId, atmId, values)
}
