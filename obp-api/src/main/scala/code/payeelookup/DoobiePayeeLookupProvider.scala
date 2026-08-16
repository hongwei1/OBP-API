package code.payeelookup

import code.api.util.DoobieUtil
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import java.sql.Timestamp
import java.util.Date

object DoobiePayeeLookupProvider extends PayeeLookupProvider {

  // Table "payeelookup" (custom dbTableName), IdPK only with custom CreationDate/ExpiresAt.
  private case class PlRow(
    lookupid: Option[String],
    identifiertype: Option[String],
    identifier: Option[String],
    fspid: Option[String],
    networkprovider: Option[String],
    fullname: Option[String],
    accountcategory: Option[String],
    accounttype: Option[String],
    identitytype: Option[String],
    identityvalue: Option[String],
    frombankid: Option[String],
    fromaccountid: Option[String],
    createdbyuserid: Option[String],
    creationdate: Option[Timestamp],
    expiresat: Option[Timestamp]
  )

  private case class DoobiePayeeLookup(row: PlRow) extends PayeeLookupTrait {
    private def opt(s: String): Option[String] = if (s == null || s.isEmpty) None else Some(s)
    override def lookupId: String = row.lookupid.getOrElse("")
    override def identifierType: String = row.identifiertype.getOrElse("")
    override def identifier: String = row.identifier.getOrElse("")
    override def fspId: Option[String] = opt(row.fspid.orNull)
    override def networkProvider: Option[String] = opt(row.networkprovider.orNull)
    override def fullName: String = row.fullname.getOrElse("")
    override def accountCategory: Option[String] = opt(row.accountcategory.orNull)
    override def accountType: Option[String] = opt(row.accounttype.orNull)
    override def identityType: Option[String] = opt(row.identitytype.orNull)
    override def identityValue: Option[String] = opt(row.identityvalue.orNull)
    override def fromBankId: String = row.frombankid.getOrElse("")
    override def fromAccountId: String = row.fromaccountid.getOrElse("")
    override def createdByUserId: String = row.createdbyuserid.getOrElse("")
    override def createdAt: Date = row.creationdate.orNull
    override def expiresAt: Date = row.expiresat.orNull
  }

  private def nn(s: String): String = if (s == null) "" else s

  private val selectCols: Fragment =
    fr"""SELECT lookupid, identifiertype, identifier, fspid, networkprovider, fullname, accountcategory,
                accounttype, identitytype, identityvalue, frombankid, fromaccountid, createdbyuserid,
                creationdate, expiresat
         FROM payeelookup"""

  override def createPayeeLookup(lookupId: String, identifierType: String, identifier: String,
                                 fspId: Option[String], networkProvider: Option[String], fullName: String,
                                 accountCategory: Option[String], accountType: Option[String],
                                 identityType: Option[String], identityValue: Option[String],
                                 fromBankId: String, fromAccountId: String, createdByUserId: String,
                                 ttlSeconds: Long): Box[PayeeLookupTrait] = {
    val now = System.currentTimeMillis()
    tryo {
      val creation = new Timestamp(now)
      val expires = new Timestamp(now + ttlSeconds * 1000)
      val fsp = nn(fspId.getOrElse(""))
      val net = nn(networkProvider.getOrElse(""))
      val accCat = nn(accountCategory.getOrElse(""))
      val accType = nn(accountType.getOrElse(""))
      val idType = nn(identityType.getOrElse(""))
      val idVal = nn(identityValue.getOrElse(""))
      DoobieUtil.runQuery(
        sql"""INSERT INTO payeelookup
                (lookupid, identifiertype, identifier, fspid, networkprovider, fullname, accountcategory,
                 accounttype, identitytype, identityvalue, frombankid, fromaccountid, createdbyuserid,
                 creationdate, expiresat)
              VALUES (${nn(lookupId)}, ${nn(identifierType)}, ${nn(identifier)}, $fsp, $net, ${nn(fullName)}, $accCat,
                      $accType, $idType, $idVal, ${nn(fromBankId)}, ${nn(fromAccountId)}, ${nn(createdByUserId)},
                      $creation, $expires)""".update.run)
      DoobiePayeeLookup(PlRow(Some(nn(lookupId)), Some(nn(identifierType)), Some(nn(identifier)), Some(fsp), Some(net),
        Some(nn(fullName)), Some(accCat), Some(accType), Some(idType), Some(idVal), Some(nn(fromBankId)),
        Some(nn(fromAccountId)), Some(nn(createdByUserId)), Some(creation), Some(expires)))
    }
  }

  override def getActivePayeeLookup(lookupId: String): Box[PayeeLookupTrait] =
    (DoobieUtil.runQuery((selectCols ++ fr"WHERE lookupid = ${nn(lookupId)} LIMIT 1").query[PlRow].option) match {
      case Some(r) => Full(DoobiePayeeLookup(r))
      case None    => Empty
    }).filter(!_.isExpired)
}
