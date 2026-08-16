package code.transactionrequests

import code.api.util.APIUtil.DateWithMsFormat
import code.api.util.ErrorMessages._
import code.api.util.{APIUtil, CallContext, CustomJsonFormats, DoobieUtil}
import code.api.v2_1_0.TransactionRequestBodyCounterpartyJSON
import code.api.v7_0_0.JSONFactory700.TransactionRequestBodyOpenCorridorJsonV700
import code.bankconnectors.LocalMappedConnectorInternal
import code.consent.Consents
import code.model.BankAccountExtended
import com.openbankproject.commons.model._
import com.openbankproject.commons.model.enums.TransactionRequestTypes.{COUNTERPARTY, SEPA}
import com.openbankproject.commons.model.enums.{AccountRoutingScheme, TransactionRequestStatus, TransactionRequestTypes}
import com.openbankproject.commons.util.json
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Failure, Full}
import org.json4s.JsonAST.{JField, JObject, JString}

import java.sql.{Date => SqlDate, Timestamp}
import java.util.{Date => JDate}

/**
 * Doobie-backed implementation of [[TransactionRequestProvider]], replacing the
 * Lift Mapper reads and writes in [[MappedTransactionRequestProvider]].
 *
 * [[getMappedTransactionRequest]] is the only method that still returns a Lift
 * Mapper entity ([[MappedTransactionRequest]]); it has no external callers and
 * is kept for trait compliance via a Lift fallback.
 */
object DoobieTransactionRequestProvider extends TransactionRequestProvider with CustomJsonFormats {

  // ── row types ──────────────────────────────────────────────────────────────

  private case class TrRow1(
    transactionRequestId: Option[String],
    trType: Option[String],
    transactionIds: Option[String],
    status: Option[String],
    startDate: Option[SqlDate],
    endDate: Option[SqlDate],
    challengeId: Option[String],
    challengeAllowedAttempts: Option[Int],
    challengeType: Option[String],
    chargeSummary: Option[String],
    chargeAmount: Option[String],
    chargeCurrency: Option[String],
    chargePolicy: Option[String],
    bodyValueCurrency: Option[String],
    bodyValueAmount: Option[String],
    bodyDescription: Option[String],
    details: Option[String],
    fromBankId: Option[String],
    fromAccountId: Option[String],
    toBankId: Option[String],
    toAccountId: Option[String]
  )

  private case class TrRow2(
    counterpartyId: Option[String],
    name: Option[String],
    thisBankId: Option[String],
    thisAccountId: Option[String],
    thisViewId: Option[String],
    otherAccRoutingScheme: Option[String],
    otherAccRoutingAddress: Option[String],
    otherBankRoutingScheme: Option[String],
    otherBankRoutingAddress: Option[String],
    isBeneficiary: Option[Boolean],
    userId: Option[String],
    onBehalfOfUserId: Option[String],
    originatorName: Option[String],
    originatorAddress: Option[String],
    originatorAccRoutingScheme: Option[String],
    originatorAccRoutingAddress: Option[String]
  )

  private case class TrRow(p1: TrRow1, p2: TrRow2)

  // ── column list (order matches TrRow1 then TrRow2) ────────────────────────

  private val selectCols: Fragment =
    fr"""SELECT
           mtransactionrequestid, mtype, mtransactionids, mstatus,
           mstartdate, menddate,
           mchallenge_id, mchallenge_allowedattempts, mchallenge_challengetype,
           mcharge_summary, mcharge_amount, mcharge_currency, mcharge_policy,
           mbody_value_currency, mbody_value_amount, mbody_description, mdetails,
           mfrom_bankid, mfrom_accountid, mto_bankid, mto_accountid,
           mcounterpartyid, mname,
           mthisbankid, mthisaccountid, mthisviewid,
           motheraccountroutingscheme, motheraccountroutingaddress,
           motherbankroutingscheme, motherbankroutingaddress,
           misbeneficiary, muserid, monbehalfofuserid,
           moriginator_name, moriginator_address,
           moriginator_accountroutingscheme, moriginator_accountroutingaddress
         FROM mappedtransactionrequest"""

  // ── helpers ────────────────────────────────────────────────────────────────

  private def g(opt: Option[String]): String = opt.getOrElse("")
  private def toJDate(d: Option[SqlDate]): JDate =
    d.map(s => new JDate(s.getTime)).getOrElse(new JDate(0L))

  // ── row → model (mirrors MappedTransactionRequest.toTransactionRequest) ───

  private def rowToTransactionRequest(row: TrRow): Option[TransactionRequest] = {
    val p1 = row.p1
    val p2 = row.p2
    val details = g(p1.details)
    val parsedDetails = if (details.nonEmpty) json.parse(details) else org.json4s.JNothing
    val transactionType = g(p1.trType)

    val tAmount = AmountOfMoney(
      currency = g(p1.bodyValueCurrency),
      amount   = g(p1.bodyValueAmount)
    )

    val tToSandboxTan =
      if (TransactionRequestTypes.withName(transactionType) == TransactionRequestTypes.SANDBOX_TAN ||
          TransactionRequestTypes.withName(transactionType) == TransactionRequestTypes.ACCOUNT_OTP ||
          TransactionRequestTypes.withName(transactionType) == TransactionRequestTypes.ACCOUNT)
        Some(TransactionRequestAccount(bank_id = g(p1.toBankId), account_id = g(p1.toAccountId)))
      else None

    val tToSepa =
      if (TransactionRequestTypes.withName(transactionType) == TransactionRequestTypes.SEPA) {
        val ibanList: List[String] = for {
          JObject(child) <- parsedDetails
          JField("iban", JString(iban)) <- child
        } yield iban
        Some(TransactionRequestIban(iban = if (ibanList.isEmpty) "" else ibanList.head))
      } else None

    val tToCounterparty =
      if (TransactionRequestTypes.withName(transactionType) == TransactionRequestTypes.COUNTERPARTY ||
          TransactionRequestTypes.withName(transactionType) == TransactionRequestTypes.CARD) {
        val cpIdList: List[String] = for {
          JObject(child) <- parsedDetails
          JField("counterparty_id", JString(v)) <- child
        } yield v
        Some(TransactionRequestCounterpartyId(
          counterparty_id = if (cpIdList.isEmpty) "" else cpIdList.head))
      } else None

    // OPEN_CORRIDOR_PROMISE's persisted body has the same `to: PostSimpleCounterpartyJson400` shape
    // as SIMPLE, so we reuse this SIMPLE branch's JSON-field extraction. (Upstream split the old
    // OPEN_CORRIDOR type into _PROMISE and _SETTLEMENT; only _PROMISE carries this body.)
    val tToSimple =
      if ((TransactionRequestTypes.withName(transactionType) == TransactionRequestTypes.SIMPLE ||
           TransactionRequestTypes.withName(transactionType) == TransactionRequestTypes.OPEN_CORRIDOR_PROMISE) &&
          details.nonEmpty) {
        val simples = for {
          JObject(child)                                                  <- parsedDetails
          JField("other_bank_routing_scheme", JString(s1))               <- child
          JField("other_bank_routing_address", JString(s2))              <- child
          JField("other_branch_routing_scheme", JString(s3))             <- child
          JField("other_branch_routing_address", JString(s4))            <- child
          JField("other_account_routing_scheme", JString(s5))            <- child
          JField("other_account_routing_address", JString(s6))           <- child
          JField("other_account_secondary_routing_scheme", JString(s7))  <- child
          JField("other_account_secondary_routing_address", JString(s8)) <- child
        } yield TransactionRequestSimple(s1, s2, s3, s4, s5, s6, s7, s8)
        Some(if (simples.isEmpty) TransactionRequestSimple("", "", "", "", "", "", "", "")
             else simples.head)
      } else None

    val tToTransferToPhone =
      if (TransactionRequestTypes.withName(transactionType) == TransactionRequestTypes.TRANSFER_TO_PHONE && details.nonEmpty)
        Some(parsedDetails.extract[TransactionRequestTransferToPhone])
      else None

    val tToTransferToAtm =
      if (TransactionRequestTypes.withName(transactionType) == TransactionRequestTypes.TRANSFER_TO_ATM && details.nonEmpty)
        Some(parsedDetails.extract[TransactionRequestTransferToAtm])
      else None

    val tToTransferToAccount =
      if (TransactionRequestTypes.withName(transactionType) == TransactionRequestTypes.TRANSFER_TO_ACCOUNT && details.nonEmpty)
        Some(parsedDetails.extract[TransactionRequestTransferToAccount])
      else None

    val tToAgent =
      if (TransactionRequestTypes.withName(transactionType) == TransactionRequestTypes.AGENT_CASH_WITHDRAWAL && details.nonEmpty) {
        val agentNums: List[String] = for {
          JObject(child) <- parsedDetails
          JField("agent_number", JString(v)) <- child
        } yield v
        val bankIds: List[String] = for {
          JObject(child) <- parsedDetails
          JField("bank_id", JString(v)) <- child
        } yield v
        Some(TransactionRequestAgentCashWithdrawal(
          bank_id      = if (bankIds.isEmpty) "" else bankIds.head,
          agent_number = if (agentNums.isEmpty) "" else agentNums.head
        ))
      } else None

    val tToSepaCreditTransfers =
      if (TransactionRequestTypes.withName(transactionType) == TransactionRequestTypes.SEPA_CREDIT_TRANSFERS && details.nonEmpty)
        Some(parsedDetails.extract[SepaCreditTransfers])
      else None

    val tBody = TransactionRequestBodyAllTypes(
      to_sandbox_tan          = tToSandboxTan,
      to_sepa                 = tToSepa,
      to_counterparty         = tToCounterparty,
      to_simple               = tToSimple,
      to_transfer_to_phone    = tToTransferToPhone,
      to_transfer_to_atm      = tToTransferToAtm,
      to_transfer_to_account  = tToTransferToAccount,
      to_sepa_credit_transfers = tToSepaCreditTransfers,
      to_agent                = tToAgent,
      value                   = tAmount,
      description             = g(p1.bodyDescription)
    )

    val tOriginator: Option[TransactionRequestOriginator] =
      p2.originatorName.filter(_.nonEmpty).map { name =>
        TransactionRequestOriginator(
          name    = name,
          address = g(p2.originatorAddress),
          account_routing = TransactionRequestOriginatorAccountRouting(
            scheme  = g(p2.originatorAccRoutingScheme),
            address = g(p2.originatorAccRoutingAddress)
          )
        )
      }

    Some(TransactionRequest(
      id                         = TransactionRequestId(g(p1.transactionRequestId)),
      `type`                     = transactionType,
      from                       = TransactionRequestAccount(bank_id = g(p1.fromBankId), account_id = g(p1.fromAccountId)),
      body                       = tBody,
      status                     = g(p1.status),
      transaction_ids            = g(p1.transactionIds),
      start_date                 = toJDate(p1.startDate),
      end_date                   = toJDate(p1.endDate),
      challenge                  = TransactionRequestChallenge(
                                     id               = g(p1.challengeId),
                                     allowed_attempts = p1.challengeAllowedAttempts.getOrElse(0),
                                     challenge_type   = g(p1.challengeType)),
      charge                     = TransactionRequestCharge(
                                     summary = g(p1.chargeSummary),
                                     value   = AmountOfMoney(currency = g(p1.chargeCurrency), amount = g(p1.chargeAmount))),
      charge_policy              = g(p1.chargePolicy),
      counterparty_id            = CounterpartyId(g(p2.counterpartyId)),
      name                       = g(p2.name),
      this_bank_id               = BankId(g(p2.thisBankId)),
      this_account_id            = AccountId(g(p2.thisAccountId)),
      this_view_id               = ViewId(g(p2.thisViewId)),
      other_account_routing_scheme  = g(p2.otherAccRoutingScheme),
      other_account_routing_address = g(p2.otherAccRoutingAddress),
      other_bank_routing_scheme     = g(p2.otherBankRoutingScheme),
      other_bank_routing_address    = g(p2.otherBankRoutingAddress),
      is_beneficiary             = p2.isBeneficiary.getOrElse(false),
      user_id                    = p2.userId.filter(_.nonEmpty),
      on_behalf_of_user_id       = p2.onBehalfOfUserId.filter(_.nonEmpty),
      originator                 = tOriginator
    ))
  }

  // ── trait implementation ───────────────────────────────────────────────────

  override def getMappedTransactionRequest(transactionRequestId: TransactionRequestId): Box[MappedTransactionRequest] =
    MappedTransactionRequest.find(
      net.liftweb.mapper.By(MappedTransactionRequest.mTransactionRequestId, transactionRequestId.value))

  override def getTransactionRequestFromProvider(transactionRequestId: TransactionRequestId): Box[TransactionRequest] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE mtransactionrequestid = ${transactionRequestId.value} LIMIT 1")
        .query[TrRow].option) match {
      case Some(row) => Full(rowToTransactionRequest(row)).flatMap {
        case Some(tr) => Full(tr)
        case None     => Failure(s"Could not build TransactionRequest for id ${transactionRequestId.value}")
      }
      case None => Failure(s"Could not find TransactionRequest with id ${transactionRequestId.value}")
    }

  override def getTransactionRequestsFromProvider(bankId: BankId, accountId: AccountId): Box[List[TransactionRequest]] =
    Full(
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE mfrom_bankid = ${bankId.value} AND mfrom_accountid = ${accountId.value}")
          .query[TrRow].to[List])
        .flatMap(rowToTransactionRequest)
    )

  override def updateAllPendingTransactionRequests: Box[Option[Unit]] = {
    val trOpt = DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE mstatus = ${TransactionRequestStatus.PENDING.toString} LIMIT 1")
        .query[TrRow].option)
    logger.debug("Updating status of all pending transactions: ")
    val statuses = LocalMappedConnectorInternal.getTransactionRequestStatuses
    trOpt.map { row =>
      for {
        transactionRequest <- rowToTransactionRequest(row)
        if statuses.exists(i => i.transactionRequestId -> i.bulkTransactionsStatus == transactionRequest.id -> List("APVD"))
      } yield {
        DoobieUtil.runQuery(
          sql"""UPDATE mappedtransactionrequest
                SET mstatus = ${TransactionRequestStatus.COMPLETED.toString}
                WHERE mtransactionrequestid = ${transactionRequest.id.value}""".update.run
        )
        logger.debug(s"updated ${transactionRequest.id} status: ${TransactionRequestStatus.COMPLETED}")
      }
    } match {
      case Some(opt) => Full(opt)
      case None      => Full(None)
    }
  }

  override def createTransactionRequestImpl210(
    transactionRequestId: TransactionRequestId,
    transactionRequestType: TransactionRequestType,
    fromAccount: BankAccount,
    toAccount: BankAccount,
    transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
    details: String,
    status: String,
    charge: TransactionRequestCharge,
    chargePolicy: String,
    paymentService: Option[String],
    berlinGroupPayments: Option[BerlinGroupTransactionRequestCommonBodyJson],
    apiStandard: Option[String],
    apiVersion: Option[String],
    callContext: Option[CallContext]
  ): Box[TransactionRequest] = {

    val toAccountRouting = TransactionRequestTypes.withName(transactionRequestType.value) match {
      case SEPA => toAccount.accountRoutings.find(_.scheme == AccountRoutingScheme.IBAN.toString)
                     .orElse(toAccount.accountRoutings.headOption)
      case _    => toAccount.accountRoutings.headOption
    }

    val counterpartyIdOpt = TransactionRequestTypes.withName(transactionRequestType.value) match {
      case COUNTERPARTY =>
        Some(transactionRequestCommonBody.asInstanceOf[TransactionRequestBodyCounterpartyJSON].to.counterparty_id)
      case _ => None
    }

    val (paymentStartDateOpt, paymentEndDateOpt, executionRuleOpt, frequencyOpt, dayOfExecutionOpt) =
      if (paymentService == Some("periodic-payments")) {
        val pf = berlinGroupPayments.asInstanceOf[Option[PeriodicSepaCreditTransfersBerlinGroupV13]]
        (
          pf.map(_.startDate).map(DateWithMsFormat.parse).map(d => new SqlDate(d.getTime)),
          pf.flatMap(_.endDate).map(DateWithMsFormat.parse).map(d => new SqlDate(d.getTime)),
          pf.flatMap(_.executionRule),
          pf.map(_.frequency),
          pf.flatMap(_.dayOfExecution)
        )
      } else (None, None, None, None, None)

    val consentIdOpt = callContext.map(_.requestHeaders).flatMap(APIUtil.getConsentIdRequestHeaderValue)
    val consentReferenceIdOpt = consentIdOpt
      .flatMap(id => Consents.consentProvider.vend.getConsentByConsentId(id).toOption)
      .map(_.consentReferenceId)

    val explicitOriginator: Option[TransactionRequestOriginator] = transactionRequestCommonBody match {
      case ob: TransactionRequestBodyOpenCorridorJsonV700 => Some(ob.originator)
      case _                                              => None
    }

    val now = new Timestamp(System.currentTimeMillis())
    val nowDate = new SqlDate(System.currentTimeMillis())

    DoobieUtil.runQuery(
      sql"""INSERT INTO mappedtransactionrequest (
              mtransactionrequestid, mtype,
              mstatus, mstartdate, menddate,
              mcharge_summary, mcharge_amount, mcharge_currency, mcharge_policy,
              mfrom_bankid, mfrom_accountid,
              mto_bankid, mto_accountid,
              mname,
              motheraccountroutingscheme, motheraccountroutingaddress,
              motherbankroutingscheme, motherbankroutingaddress,
              mcounterpartyid,
              mbody_value_currency, mbody_value_amount, mbody_description, mdetails,
              mpaymentstartdate, mpaymentenddate,
              mpaymentexecutionrule, mpaymentfrequency, mpaymentdayofexecution,
              mconsentreferenceid,
              mapiversion, mapistandard,
              muserid, monbehalfofuserid,
              moriginator_name, moriginator_address,
              moriginator_accountroutingscheme, moriginator_accountroutingaddress,
              createdat, updatedat
            ) VALUES (
              ${transactionRequestId.value}, ${transactionRequestType.value},
              $status, $nowDate, $nowDate,
              ${charge.summary}, ${charge.value.amount}, ${charge.value.currency}, $chargePolicy,
              ${fromAccount.bankId.value}, ${fromAccount.accountId.value},
              ${toAccount.bankId.value}, ${toAccount.accountId.value},
              ${toAccount.name},
              ${toAccountRouting.map(_.scheme).getOrElse("")},
              ${toAccountRouting.map(_.address).getOrElse("")},
              ${toAccount.attributes.flatMap(_.find(_.name == "BANK_ROUTING_SCHEME").map(_.value)).getOrElse(BankAccountExtended(toAccount).bankRoutingScheme)},
              ${toAccount.attributes.flatMap(_.find(_.name == "BANK_ROUTING_ADDRESS").map(_.value)).getOrElse(BankAccountExtended(toAccount).bankRoutingScheme)},
              $counterpartyIdOpt,
              ${transactionRequestCommonBody.value.currency},
              ${transactionRequestCommonBody.value.amount},
              ${transactionRequestCommonBody.description},
              $details,
              $paymentStartDateOpt, $paymentEndDateOpt,
              $executionRuleOpt, $frequencyOpt, $dayOfExecutionOpt,
              $consentReferenceIdOpt,
              $apiVersion, $apiStandard,
              ${callContext.flatMap(_.user.map(_.userId))},
              ${callContext.flatMap(cc => cc.onBehalfOfUser.or(cc.consenter).map(_.userId))},
              ${explicitOriginator.map(_.name)},
              ${explicitOriginator.map(_.address)},
              ${explicitOriginator.map(_.account_routing.scheme)},
              ${explicitOriginator.map(_.account_routing.address)},
              $now, $now
            )""".update.run)

    getTransactionRequestFromProvider(transactionRequestId)
  }

  override def saveTransactionRequestTransactionImpl(
    transactionRequestId: TransactionRequestId,
    transactionId: TransactionId
  ): Box[Boolean] = {
    val rows = DoobieUtil.runQuery(
      sql"""UPDATE mappedtransactionrequest
            SET mtransactionids = ${transactionId.value}, updatedat = ${new Timestamp(System.currentTimeMillis())}
            WHERE mtransactionrequestid = ${transactionRequestId.value}""".update.run)
    if (rows > 0) Full(true)
    else Failure(s"$SaveTransactionRequestTransactionException Couldn't find transaction request ${transactionRequestId.value}")
  }

  override def saveTransactionRequestChallengeImpl(
    transactionRequestId: TransactionRequestId,
    challenge: TransactionRequestChallenge
  ): Box[Boolean] = {
    val now = new Timestamp(System.currentTimeMillis())
    val rows = DoobieUtil.runQuery(
      sql"""UPDATE mappedtransactionrequest
            SET mchallenge_id = ${challenge.id},
                mchallenge_allowedattempts = ${challenge.allowed_attempts},
                mchallenge_challengetype = ${challenge.challenge_type},
                updatedat = $now
            WHERE mtransactionrequestid = ${transactionRequestId.value}""".update.run)
    if (rows > 0) Full(true)
    else Failure(s"$SaveTransactionRequestChallengeException Couldn't find transaction request ${transactionRequestId.value} to set transactionId")
  }

  override def saveTransactionRequestStatusImpl(
    transactionRequestId: TransactionRequestId,
    status: String
  ): Box[Boolean] = {
    val rows = DoobieUtil.runQuery(
      sql"""UPDATE mappedtransactionrequest
            SET mstatus = $status, updatedat = ${new Timestamp(System.currentTimeMillis())}
            WHERE mtransactionrequestid = ${transactionRequestId.value}""".update.run)
    if (rows > 0) Full(true)
    else Failure(s"$SaveTransactionRequestStatusException Couldn't find transaction request ${transactionRequestId.value} to set status")
  }

  override def saveTransactionRequestDescriptionImpl(
    transactionRequestId: TransactionRequestId,
    description: String
  ): Box[Boolean] = {
    val rows = DoobieUtil.runQuery(
      sql"""UPDATE mappedtransactionrequest
            SET mbody_description = $description, updatedat = ${new Timestamp(System.currentTimeMillis())}
            WHERE mtransactionrequestid = ${transactionRequestId.value}""".update.run)
    if (rows > 0) Full(true)
    else Failure(s"$SaveTransactionRequestDescriptionException Couldn't find transaction request ${transactionRequestId.value} to set description")
  }

  override def bulkDeleteTransactionRequestsByTransactionId(transactionId: TransactionId): Boolean =
    DoobieUtil.runQuery(
      sql"DELETE FROM mappedtransactionrequest WHERE mtransactionids = ${transactionId.value}"
        .update.run) >= 0

  override def bulkDeleteTransactionRequests(): Boolean = {
    DoobieUtil.runQuery(sql"DELETE FROM mappedtransactionrequest".update.run)
    true
  }
}
