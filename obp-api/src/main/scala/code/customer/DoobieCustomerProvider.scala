package code.customer

import java.lang
import java.sql.Timestamp
import java.util.Date

import code.CustomerDependants.CustomerDependants
import code.api.util._
import code.usercustomerlinks.UserCustomerLink
import code.users.Users
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model._
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.List
import scala.concurrent.Future

object DoobieCustomerProvider extends CustomerProvider with MdcLoggable {

  private def nn(s: String): String = if (s == null) "" else s
  private def ts(d: Date): Option[Timestamp] = Option(d).map(x => new Timestamp(x.getTime))
  private def tsReq(d: Date): Timestamp = new Timestamp(d.getTime)
  private def optDate(t: Option[Timestamp]): Date = t.map(x => new Date(x.getTime)).getOrElse(new Date(0))

  private case class Row(
    id: Long,
    mcustomerid: String,
    mbank: String,
    mnumber: String,
    mmobilenumber: String,
    mlegalname: String,
    memail: String,
    mfaceimageurl: String,
    mfaceimagetime: Option[Timestamp],
    mdateofbirth: Option[Timestamp],
    mrelationshipstatus: String,
    mdependents: Int,
    mhighesteducationattained: String,
    memploymentstatus: String,
    mcreditrating: String,
    mcreditsource: String,
    mcreditlimitcurrency: String,
    mcreditlimitamount: String,
    mkycstatus: Boolean,
    mlastokdate: Option[Timestamp],
    mtitle: String,
    mbranchid: String,
    mnamesuffix: String,
    mcustomertype: String,
    mparentcustomerid: String,
    mispendingagent: Boolean,
    misconfirmedagent: Boolean
  )

  private class CustomerCommons(row: Row, dobs: List[Date]) extends Customer with Agent {
    override def customerId: String               = row.mcustomerid
    override def bankId: String                   = row.mbank
    override def number: String                   = row.mnumber
    override def mobileNumber: String             = row.mmobilenumber
    override def legalName: String                = row.mlegalname
    override def email: String                    = row.memail
    override def faceImage: CustomerFaceImageTrait = new CustomerFaceImageTrait {
      override def url: String = row.mfaceimageurl
      override def date: Date  = optDate(row.mfaceimagetime)
    }
    override def dateOfBirth: Date                = optDate(row.mdateofbirth)
    override def relationshipStatus: String       = row.mrelationshipstatus
    override def dependents: Integer              = Int.box(row.mdependents)
    override def dobOfDependents: List[Date]      = dobs
    override def highestEducationAttained: String = row.mhighesteducationattained
    override def employmentStatus: String         = row.memploymentstatus
    override def creditRating: CreditRatingTrait  = new CreditRatingTrait {
      override def rating: String = row.mcreditrating
      override def source: String = row.mcreditsource
    }
    override def creditLimit: AmountOfMoneyTrait  = new AmountOfMoneyTrait {
      override def currency: String = row.mcreditlimitcurrency
      override def amount: String   = row.mcreditlimitamount
    }
    override def kycStatus: lang.Boolean          = Boolean.box(row.mkycstatus)
    override def lastOkDate: Date                 = optDate(row.mlastokdate)
    override def title: String                    = row.mtitle
    override def branchId: String                 = row.mbranchid
    override def nameSuffix: String               = row.mnamesuffix
    // Mirror Lift's `Option(mCustomerType.get)` exactly: MappedString.get never returns null,
    // so an empty column is Some("") (NOT None). Filtering to None here diverged from Lift and
    // made the v6 JSON factory emit "INDIVIDUAL" (its getOrElse default) instead of "".
    override def customerType: Option[String]     = Option(row.mcustomertype)
    override def parentCustomerId: Option[String] = Option(row.mparentcustomerid)
    override def agentId: String                  = row.mcustomerid
    override def isConfirmedAgent: Boolean        = row.misconfirmedagent
    override def isPendingAgent: Boolean          = row.mispendingagent
  }

  private def toCommons(row: Row): Customer with Agent = {
    val dobs = CustomerDependants.CustomerDependants.vend
      .getCustomerDependantsByCustomerPrimaryKey(row.id)
      .map(_.mDateOfBirth.get)
    new CustomerCommons(row, dobs)
  }

  private val selectCols: Fragment = fr"""SELECT
    id, mcustomerid, mbank, mnumber, mmobilenumber, mlegalname, memail,
    mfaceimageurl, mfaceimagetime, mdateofbirth, mrelationshipstatus, mdependents,
    mhighesteducationattained, memploymentstatus, mcreditrating, mcreditsource,
    mcreditlimitcurrency, mcreditlimitamount, mkycstatus, mlastokdate,
    mtitle, mbranchid, mnamesuffix, mcustomertype, mparentcustomerid,
    mispendingagent, misconfirmedagent
  FROM mappedcustomer"""

  private def whereClause(conditions: List[Fragment]): Fragment =
    if (conditions.isEmpty) Fragment.empty
    else fr"WHERE" ++ conditions.reduceLeft((a, b) => a ++ fr"AND" ++ b)

  private def dateConditions(queryParams: List[OBPQueryParam]): List[Fragment] =
    queryParams.collect { case OBPFromDate(d) => fr"updatedat >= ${tsReq(d)}" } :::
    queryParams.collect { case OBPToDate(d)   => fr"updatedat <= ${tsReq(d)}" }

  private def paginationClause(queryParams: List[OBPQueryParam]): Fragment = {
    val orderFr = queryParams.collectFirst {
      case OBPOrdering(_, OBPAscending)  => fr"ORDER BY mlastokdate ASC"
      case OBPOrdering(_, OBPDescending) => fr"ORDER BY mlastokdate DESC"
    }.getOrElse(Fragment.empty)
    val limitFr  = queryParams.collectFirst { case OBPLimit(v)  => fr"LIMIT $v"  }.getOrElse(Fragment.empty)
    val offsetFr = queryParams.collectFirst { case OBPOffset(v) => fr"OFFSET $v" }.getOrElse(Fragment.empty)
    orderFr ++ limitFr ++ offsetFr
  }

  private def runOne(conditions: List[Fragment]): Box[Customer with Agent] =
    DoobieUtil.runQuery(
      (selectCols ++ whereClause(conditions) ++ fr"LIMIT 1").query[Row].option) match {
      case Some(row) => Full(toCommons(row))
      case None      => Empty
    }

  private def runList(conditions: List[Fragment], queryParams: List[OBPQueryParam] = Nil): List[Customer] =
    DoobieUtil.runQuery(
      (selectCols ++ whereClause(conditions ::: dateConditions(queryParams)) ++ paginationClause(queryParams))
        .query[Row].to[List]).map(toCommons)

  override def getCustomersAtAllBanks(queryParams: List[OBPQueryParam]): Future[Box[List[Customer]]] =
    Future { Full(runList(Nil, queryParams)) }

  override def getCustomersFuture(bankId: BankId, queryParams: List[OBPQueryParam]): Future[Box[List[Customer]]] =
    Future { Full(runList(List(fr"mbank = ${nn(bankId.value)}"), queryParams)) }

  override def getCustomersByCustomerPhoneNumber(bankId: BankId, phoneNumber: String): Future[Box[List[Customer]]] =
    Future {
      Full(runList(List(fr"mbank = ${nn(bankId.value)}", fr"mmobilenumber LIKE ${nn(phoneNumber)}")))
    }

  override def getCustomersByCustomerLegalName(bankId: BankId, legalName: String): Future[Box[List[Customer]]] =
    Future {
      Full(runList(List(fr"mbank = ${nn(bankId.value)}", fr"mlegalname LIKE ${nn(legalName)}")))
    }

  override def getCustomerByUserId(bankId: BankId, userId: String): Box[Customer] = {
    val customerId = UserCustomerLink.userCustomerLink.vend.getUserCustomerLinksByUserId(userId) match {
      case x :: _ => x.customerId
      case _      => "There is no linked customer to this user"
    }
    getCustomerByCustomerId(customerId)
  }

  override def getCustomersByUserId(userId: String): List[Customer] = {
    val customerIds = UserCustomerLink.userCustomerLink.vend.getUserCustomerLinksByUserId(userId).map(_.customerId)
    if (customerIds.isEmpty) Nil
    else {
      val inFragments = customerIds.map(id => fr"${nn(id)}")
      val inClause = inFragments.reduceLeft((a, b) => a ++ fr"," ++ b)
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE mcustomerid IN (" ++ inClause ++ fr")")
          .query[Row].to[List]).map(toCommons)
    }
  }

  override def getCustomersByUserIdFuture(userId: String): Future[Box[List[Customer]]] =
    Future { Full(getCustomersByUserId(userId)) }

  override def getCustomerByCustomerId(customerId: String): Box[Customer] =
    runOne(List(fr"mcustomerid = ${nn(customerId)}"))

  override def getCustomerByCustomerIdFuture(customerId: String): Future[Box[Customer]] =
    Future { getCustomerByCustomerId(customerId) }

  override def getBankIdByCustomerId(customerId: String): Box[String] =
    DoobieUtil.runQuery(
      fr"SELECT mbank FROM mappedcustomer WHERE mcustomerid = ${nn(customerId)} LIMIT 1"
        .query[String].option) match {
      case Some(b) => Full(b)
      case None    => Empty
    }

  override def getCustomerByCustomerNumber(customerNumber: String, bankId: BankId): Box[Customer] =
    runOne(List(fr"mnumber = ${nn(customerNumber)}", fr"mbank = ${nn(bankId.value)}"))

  override def getCustomerByCustomerNumberFuture(customerNumber: String, bankId: BankId): Future[Box[Customer]] =
    Future { getCustomerByCustomerNumber(customerNumber, bankId) }

  override def getUser(bankId: BankId, customerNumber: String): Box[User] =
    getCustomerByCustomerNumber(customerNumber, bankId).flatMap { c =>
      UserCustomerLink.userCustomerLink.vend.getUserCustomerLinkByCustomerId(c.customerId)
    }.flatMap { y =>
      Users.users.vend.getUserByUserId(y.userId)
    }

  override def checkCustomerNumberAvailable(bankId: BankId, customerNumber: String): Boolean =
    DoobieUtil.runQuery(
      fr"SELECT COUNT(*) FROM mappedcustomer WHERE mbank = ${nn(bankId.value)} AND mnumber = ${nn(customerNumber)}"
        .query[Long].unique) == 0

  override def addCustomer(
    bankId: BankId,
    number: String,
    legalName: String,
    mobileNumber: String,
    email: String,
    faceImage: CustomerFaceImageTrait,
    dateOfBirth: Date,
    relationshipStatus: String,
    dependents: Int,
    dobOfDependents: List[Date],
    highestEducationAttained: String,
    employmentStatus: String,
    kycStatus: Boolean,
    lastOkDate: Date,
    creditRating: Option[CreditRatingTrait],
    creditLimit: Option[AmountOfMoneyTrait],
    title: String,
    branchId: String,
    nameSuffix: String,
    customerType: String = "",
    parentCustomerId: String = ""
  ): Box[Customer] = tryo {
    val now              = new Timestamp(System.currentTimeMillis())
    val newCustomerId    = APIUtil.generateUUID()
    val creditRatingStr  = creditRating.map(_.rating).getOrElse("")
    val creditSourceStr  = creditRating.map(_.source).getOrElse("")
    val creditLimitCurr  = creditLimit.map(_.currency).getOrElse("")
    val creditLimitAmt   = creditLimit.map(_.amount).getOrElse("")
    val faceImageUrl     = nn(faceImage.url)
    val faceImageTime    = ts(faceImage.date)   // Option[Timestamp], null-safe
    val dobTs            = ts(dateOfBirth)       // Option[Timestamp], null-safe
    val lastOkTs         = ts(lastOkDate)        // Option[Timestamp], null-safe

    DoobieUtil.runQuery(sql"""
      INSERT INTO mappedcustomer
        (mcustomerid, mbank, mnumber, mmobilenumber, mlegalname, memail,
         mfaceimageurl, mfaceimagetime, mdateofbirth, mrelationshipstatus, mdependents,
         mhighesteducationattained, memploymentstatus, mcreditrating, mcreditsource,
         mcreditlimitcurrency, mcreditlimitamount, mkycstatus, mlastokdate,
         mtitle, mbranchid, mnamesuffix, mcustomertype, mparentcustomerid,
         mispendingagent, misconfirmedagent, createdat, updatedat)
      VALUES
        ($newCustomerId, ${nn(bankId.value)}, ${nn(number)}, ${nn(mobileNumber)}, ${nn(legalName)}, ${nn(email)},
         $faceImageUrl, $faceImageTime, $dobTs, ${nn(relationshipStatus)}, $dependents,
         ${nn(highestEducationAttained)}, ${nn(employmentStatus)}, $creditRatingStr, $creditSourceStr,
         $creditLimitCurr, $creditLimitAmt, $kycStatus, $lastOkTs,
         ${nn(title)}, ${nn(branchId)}, ${nn(nameSuffix)}, ${nn(customerType)}, ${nn(parentCustomerId)},
         true, false, $now, $now)""".update.run)

    val pk = DoobieUtil.runQuery(
      fr"SELECT id FROM mappedcustomer WHERE mcustomerid = $newCustomerId LIMIT 1".query[Long].unique)

    CustomerDependants.CustomerDependants.vend
      .createCustomerDependants(pk, dobOfDependents.map(CustomerDependant(_)))

    getCustomerByCustomerId(newCustomerId)
      .openOrThrowException(s"Failed to re-read customer after insert: $newCustomerId")
  }

  private def buildUpdate(customerId: String, parts: List[Fragment]): Box[Customer] = {
    if (parts.isEmpty) return runOne(List(fr"mcustomerid = ${nn(customerId)}"))
    val now = new Timestamp(System.currentTimeMillis())
    val allParts = parts :+ fr"updatedat = $now"
    val setClauses = allParts.reduceLeft((a, b) => a ++ fr"," ++ b)
    DoobieUtil.runQuery(
      (fr"UPDATE mappedcustomer SET" ++ setClauses ++ fr"WHERE mcustomerid = ${nn(customerId)}").update.run)
    runOne(List(fr"mcustomerid = ${nn(customerId)}"))
  }

  override def updateCustomerScaData(
    customerId: String,
    mobileNumber: Option[String],
    email: Option[String],
    customerNumber: Option[String]
  ): Future[Box[Customer]] = Future {
    buildUpdate(customerId, List(
      mobileNumber.map(v => fr"mmobilenumber = ${nn(v)}"),
      email.map(v => fr"memail = ${nn(v)}"),
      customerNumber.map(v => fr"mnumber = ${nn(v)}")
    ).flatten)
  }

  override def updateCustomerCreditData(
    customerId: String,
    creditRating: Option[String],
    creditSource: Option[String],
    creditLimit: Option[AmountOfMoney]
  ): Future[Box[Customer]] = Future {
    buildUpdate(customerId, List(
      creditRating.map(v => fr"mcreditrating = ${nn(v)}"),
      creditSource.map(v => fr"mcreditsource = ${nn(v)}"),
      creditLimit.map(cl => fr"mcreditlimitamount = ${nn(cl.amount)}"),
      creditLimit.map(cl => fr"mcreditlimitcurrency = ${nn(cl.currency)}")
    ).flatten)
  }

  override def updateCustomerGeneralData(
    customerId: String,
    legalName: Option[String],
    faceImage: Option[CustomerFaceImageTrait],
    dateOfBirth: Option[Date],
    relationshipStatus: Option[String],
    dependents: Option[Int],
    highestEducationAttained: Option[String],
    employmentStatus: Option[String],
    title: Option[String],
    branchId: Option[String],
    nameSuffix: Option[String],
    customerType: Option[String] = None,
    parentCustomerId: Option[String] = None
  ): Future[Box[Customer]] = Future {
    buildUpdate(customerId, List(
      legalName.map(v => fr"mlegalname = ${nn(v)}"),
      faceImage.map(fi => fr"mfaceimageurl = ${nn(fi.url)}"),
      faceImage.map(fi => fr"mfaceimagetime = ${ts(fi.date)}"),
      dateOfBirth.map(d => fr"mdateofbirth = ${ts(d)}"),
      relationshipStatus.map(v => fr"mrelationshipstatus = ${nn(v)}"),
      dependents.map(v => fr"mdependents = $v"),
      highestEducationAttained.map(v => fr"mhighesteducationattained = ${nn(v)}"),
      employmentStatus.map(v => fr"memploymentstatus = ${nn(v)}"),
      title.map(v => fr"mtitle = ${nn(v)}"),
      branchId.map(v => fr"mbranchid = ${nn(v)}"),
      nameSuffix.map(v => fr"mnamesuffix = ${nn(v)}"),
      customerType.map(v => fr"mcustomertype = ${nn(v)}"),
      parentCustomerId.map(v => fr"mparentcustomerid = ${nn(v)}")
    ).flatten)
  }

  override def getCustomersByParentCustomerId(bankId: BankId, parentCustomerId: String): Future[Box[List[Customer]]] =
    Future {
      Full(runList(List(fr"mbank = ${nn(bankId.value)}", fr"mparentcustomerid = ${nn(parentCustomerId)}")))
    }

  override def getCustomersByCustomerTypes(bankId: BankId, customerTypes: List[String], queryParams: List[OBPQueryParam]): Future[Box[List[Customer]]] =
    Future {
      if (customerTypes.isEmpty) Full(Nil)
      else {
        val inFragments = customerTypes.map(t => fr"${nn(t)}")
        val inClause = inFragments.reduceLeft((a, b) => a ++ fr"," ++ b)
        Full(runList(
          List(fr"mbank = ${nn(bankId.value)}", fr"mcustomertype IN (" ++ inClause ++ fr")"),
          queryParams))
      }
    }

  override def bulkDeleteCustomers(): Boolean =
    DoobieUtil.runQuery(sql"DELETE FROM mappedcustomer".update.run) >= 0

  override def populateMissingUUIDs(): Boolean = {
    logger.warn("Executed script: populateMissingUUIDs")
    val pks = DoobieUtil.runQuery(
      fr"SELECT id FROM mappedcustomer WHERE mcustomerid IS NULL OR mcustomerid = ''"
        .query[Long].to[List])
    pks.foreach { pk =>
      val newUuid = APIUtil.generateUUID()
      DoobieUtil.runQuery(
        sql"UPDATE mappedcustomer SET mcustomerid = $newUuid WHERE id = $pk".update.run)
    }
    true
  }
}
