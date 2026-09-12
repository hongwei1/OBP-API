package com.tesobe.obpatms

import com.openbankproject.commons.model._

import java.sql.ResultSet

/**
  * Owns the `branches` table. Mirrors code.branches.MappedBranches field for field, including
  * two of its less obvious choices: branchRouting falls back to scheme "BRANCH_ID" / the branch's
  * own id when the routing columns are blank, and accessibleFeatures/branchType/moreInfo/
  * phoneNumber are always `Some(...)` (even `Some("")`), never `None` — the mapped table's
  * accessor never treats blank as absent for those four fields the way it does for isAccessible.
  */
class BranchRepo(db: Database) {

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

  private def fromRow(rs: ResultSet): BranchTCommons = {
    val branchId = rs.getString("branch_id")
    val routingScheme = rs.getString("branch_routing_scheme")
    val routingAddress = rs.getString("branch_routing_address")
    BranchTCommons(
      branchId = BranchId(branchId),
      bankId = BankId(rs.getString("bank_id")),
      name = rs.getString("name"),
      address = Address(
        line1 = rs.getString("line1"), line2 = rs.getString("line2"), line3 = rs.getString("line3"),
        city = rs.getString("city"), county = unblank(rs.getString("county")), state = rs.getString("state"),
        countryCode = rs.getString("country_code"), postCode = rs.getString("post_code")
      ),
      location = Location(rs.getDouble("location_latitude"), rs.getDouble("location_longitude"), None, None),
      lobbyString = Some(LobbyString(rs.getString("lobby_hours"))),
      driveUpString = Some(DriveUpString(rs.getString("drive_up_hours"))),
      meta = Meta(License(rs.getString("license_id"), rs.getString("license_name"))),
      branchRouting = Some(Routing(
        scheme = if (routingScheme == null || routingScheme.isEmpty) "BRANCH_ID" else routingScheme,
        address = if (routingAddress == null || routingAddress.isEmpty) branchId else routingAddress
      )),
      lobby = Some(Lobby(
        monday = List(OpeningTimes(rs.getString("lobby_opening_time_on_monday"), rs.getString("lobby_closing_time_on_monday"))),
        tuesday = List(OpeningTimes(rs.getString("lobby_opening_time_on_tuesday"), rs.getString("lobby_closing_time_on_tuesday"))),
        wednesday = List(OpeningTimes(rs.getString("lobby_opening_time_on_wednesday"), rs.getString("lobby_closing_time_on_wednesday"))),
        thursday = List(OpeningTimes(rs.getString("lobby_opening_time_on_thursday"), rs.getString("lobby_closing_time_on_thursday"))),
        friday = List(OpeningTimes(rs.getString("lobby_opening_time_on_friday"), rs.getString("lobby_closing_time_on_friday"))),
        saturday = List(OpeningTimes(rs.getString("lobby_opening_time_on_saturday"), rs.getString("lobby_closing_time_on_saturday"))),
        sunday = List(OpeningTimes(rs.getString("lobby_opening_time_on_sunday"), rs.getString("lobby_closing_time_on_sunday")))
      )),
      driveUp = Some(DriveUp(
        monday = OpeningTimes(rs.getString("drive_up_opening_time_on_monday"), rs.getString("drive_up_closing_time_on_monday")),
        tuesday = OpeningTimes(rs.getString("drive_up_opening_time_on_tuesday"), rs.getString("drive_up_closing_time_on_tuesday")),
        wednesday = OpeningTimes(rs.getString("drive_up_opening_time_on_wednesday"), rs.getString("drive_up_closing_time_on_wednesday")),
        thursday = OpeningTimes(rs.getString("drive_up_opening_time_on_thursday"), rs.getString("drive_up_closing_time_on_thursday")),
        friday = OpeningTimes(rs.getString("drive_up_opening_time_on_friday"), rs.getString("drive_up_closing_time_on_friday")),
        saturday = OpeningTimes(rs.getString("drive_up_opening_time_on_saturday"), rs.getString("drive_up_closing_time_on_saturday")),
        sunday = OpeningTimes(rs.getString("drive_up_opening_time_on_sunday"), rs.getString("drive_up_closing_time_on_sunday"))
      )),
      isAccessible = unflag(rs.getString("is_accessible")),
      accessibleFeatures = Some(rs.getString("accessible_features")),
      branchType = Some(rs.getString("branch_type")),
      moreInfo = Some(rs.getString("more_info")),
      phoneNumber = Some(rs.getString("phone_number")),
      isDeleted = Some(rs.getBoolean("is_deleted"))
    )
  }

  def upsert(branch: BranchT): BranchTCommons = db.withConnection { conn =>
    val lobby = branch.lobby
    // Extract per-day scalars from the lobby/driveUp structures (first element only — the mapped
    // schema this replaces stores one opening/closing pair per day, same as MappedBranches).
    def lobbyDay(sel: Lobby => List[OpeningTimes]): (String, String) =
      lobby.flatMap(l => sel(l).headOption).map(t => (t.openingTime, t.closingTime)).getOrElse(("", ""))
    def driveUpDay(sel: DriveUp => OpeningTimes): (String, String) =
      branch.driveUp.map(sel).map(t => (t.openingTime, t.closingTime)).getOrElse(("", ""))

    val (lMonOpen, lMonClose) = lobbyDay(_.monday)
    val (lTueOpen, lTueClose) = lobbyDay(_.tuesday)
    val (lWedOpen, lWedClose) = lobbyDay(_.wednesday)
    val (lThuOpen, lThuClose) = lobbyDay(_.thursday)
    val (lFriOpen, lFriClose) = lobbyDay(_.friday)
    val (lSatOpen, lSatClose) = lobbyDay(_.saturday)
    val (lSunOpen, lSunClose) = lobbyDay(_.sunday)
    val (dMonOpen, dMonClose) = driveUpDay(_.monday)
    val (dTueOpen, dTueClose) = driveUpDay(_.tuesday)
    val (dWedOpen, dWedClose) = driveUpDay(_.wednesday)
    val (dThuOpen, dThuClose) = driveUpDay(_.thursday)
    val (dFriOpen, dFriClose) = driveUpDay(_.friday)
    val (dSatOpen, dSatClose) = driveUpDay(_.saturday)
    val (dSunOpen, dSunClose) = driveUpDay(_.sunday)

    // createOrUpdateBranch is a full-row replace; see the comment on AtmRepo.upsert for why this
    // is DELETE-then-INSERT rather than `INSERT ... ON CONFLICT` (H2 2.2.220 rejects that syntax
    // even in MODE=PostgreSQL, verified directly against the driver).
    val deleteExisting = conn.prepareStatement("DELETE FROM branches WHERE bank_id = ? AND branch_id = ?")
    try {
      deleteExisting.setString(1, branch.bankId.value)
      deleteExisting.setString(2, branch.branchId.value)
      deleteExisting.executeUpdate()
    } finally deleteExisting.close()

    val sql =
      """
      |INSERT INTO branches (bank_id, branch_id, name, line1, line2, line3, city, county, state, country_code, post_code,
      |  location_latitude, location_longitude, license_id, license_name, lobby_hours, drive_up_hours,
      |  branch_routing_scheme, branch_routing_address,
      |  lobby_opening_time_on_monday, lobby_closing_time_on_monday, lobby_opening_time_on_tuesday, lobby_closing_time_on_tuesday,
      |  lobby_opening_time_on_wednesday, lobby_closing_time_on_wednesday, lobby_opening_time_on_thursday, lobby_closing_time_on_thursday,
      |  lobby_opening_time_on_friday, lobby_closing_time_on_friday, lobby_opening_time_on_saturday, lobby_closing_time_on_saturday,
      |  lobby_opening_time_on_sunday, lobby_closing_time_on_sunday,
      |  drive_up_opening_time_on_monday, drive_up_closing_time_on_monday, drive_up_opening_time_on_tuesday, drive_up_closing_time_on_tuesday,
      |  drive_up_opening_time_on_wednesday, drive_up_closing_time_on_wednesday, drive_up_opening_time_on_thursday, drive_up_closing_time_on_thursday,
      |  drive_up_opening_time_on_friday, drive_up_closing_time_on_friday, drive_up_opening_time_on_saturday, drive_up_closing_time_on_saturday,
      |  drive_up_opening_time_on_sunday, drive_up_closing_time_on_sunday,
      |  is_accessible, accessible_features, branch_type, more_info, phone_number, is_deleted)
      |VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      """.stripMargin
    val ps = conn.prepareStatement(sql)
    try {
      var i = 0
      def next(): Int = { i += 1; i }
      ps.setString(next(), branch.bankId.value)
      ps.setString(next(), branch.branchId.value)
      ps.setString(next(), branch.name)
      ps.setString(next(), branch.address.line1)
      ps.setString(next(), branch.address.line2)
      ps.setString(next(), branch.address.line3)
      ps.setString(next(), branch.address.city)
      ps.setString(next(), blank(branch.address.county))
      ps.setString(next(), branch.address.state)
      ps.setString(next(), branch.address.countryCode)
      ps.setString(next(), branch.address.postCode)
      ps.setDouble(next(), branch.location.latitude)
      ps.setDouble(next(), branch.location.longitude)
      ps.setString(next(), branch.meta.license.id)
      ps.setString(next(), branch.meta.license.name)
      ps.setString(next(), branch.lobbyString.map(_.hours).getOrElse(""))
      ps.setString(next(), branch.driveUpString.map(_.hours).getOrElse(""))
      ps.setString(next(), branch.branchRouting.map(_.scheme).getOrElse(""))
      ps.setString(next(), branch.branchRouting.map(_.address).getOrElse(""))
      ps.setString(next(), lMonOpen); ps.setString(next(), lMonClose)
      ps.setString(next(), lTueOpen); ps.setString(next(), lTueClose)
      ps.setString(next(), lWedOpen); ps.setString(next(), lWedClose)
      ps.setString(next(), lThuOpen); ps.setString(next(), lThuClose)
      ps.setString(next(), lFriOpen); ps.setString(next(), lFriClose)
      ps.setString(next(), lSatOpen); ps.setString(next(), lSatClose)
      ps.setString(next(), lSunOpen); ps.setString(next(), lSunClose)
      ps.setString(next(), dMonOpen); ps.setString(next(), dMonClose)
      ps.setString(next(), dTueOpen); ps.setString(next(), dTueClose)
      ps.setString(next(), dWedOpen); ps.setString(next(), dWedClose)
      ps.setString(next(), dThuOpen); ps.setString(next(), dThuClose)
      ps.setString(next(), dFriOpen); ps.setString(next(), dFriClose)
      ps.setString(next(), dSatOpen); ps.setString(next(), dSatClose)
      ps.setString(next(), dSunOpen); ps.setString(next(), dSunClose)
      ps.setString(next(), flag(branch.isAccessible))
      ps.setString(next(), blank(branch.accessibleFeatures))
      ps.setString(next(), blank(branch.branchType))
      ps.setString(next(), blank(branch.moreInfo))
      ps.setString(next(), blank(branch.phoneNumber))
      ps.setBoolean(next(), branch.isDeleted.getOrElse(false))
      ps.executeUpdate()
    } finally ps.close()
    findOne(branch.bankId, branch.branchId).getOrElse(
      throw new IllegalStateException(s"upsert of branch ${branch.bankId.value}/${branch.branchId.value} did not produce a row"))
  }

  def findOne(bankId: BankId, branchId: BranchId): Option[BranchTCommons] = db.withConnection { conn =>
    val ps = conn.prepareStatement("SELECT * FROM branches WHERE bank_id = ? AND branch_id = ?")
    try {
      ps.setString(1, bankId.value)
      ps.setString(2, branchId.value)
      val rs = ps.executeQuery()
      try if (rs.next()) Some(fromRow(rs)) else None
      finally rs.close()
    } finally ps.close()
  }

  /** Same pagination note as AtmRepo.findByBank: the mapped connector this replaces
    * (code.branches.MappedBranchesProvider) ignores limit/offset/date-range for getBranches too. */
  def findByBank(bankId: BankId): List[BranchTCommons] = db.withConnection { conn =>
    val ps = conn.prepareStatement("SELECT * FROM branches WHERE bank_id = ? ORDER BY branch_id")
    try {
      ps.setString(1, bankId.value)
      val rs = ps.executeQuery()
      try Iterator.continually(rs).takeWhile(_.next()).map(fromRow).toList
      finally rs.close()
    } finally ps.close()
  }
}
