package code.customer.agent

import code.api.util._
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model._
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}

import java.sql.Timestamp
import scala.concurrent.Future


/**
 * Doobie implementation of [[AgentProvider]].
 *
 * In OBP, customer and agent share the same MappedCustomer model, so this
 * provider reads and writes the `mappedcustomer` table — mirroring
 * [[MappedAgentProvider]] exactly. Only the Agent-trait fields are exposed:
 * agentId, bankId, number, legalName, mobileNumber, isPendingAgent,
 * isConfirmedAgent.
 *
 * Table:   mappedcustomer
 * Columns: mcustomerid, mbank, mnumber, mmobilenumber, mlegalname,
 *          mispendingagent, misconfirmedagent (Agent fields);
 *          mlastokdate (ordering); mcustomertype, mparentcustomerid,
 *          createdat, updatedat (defaults written by Lift's saveMe()).
 */
object DoobieAgentProvider extends AgentProvider with MdcLoggable {

  private def nn(s: String): String = if (s == null) "" else s

  private def now: Timestamp = new Timestamp(System.currentTimeMillis())

  // Row containing only the Agent-trait columns we read from mappedcustomer.
  private case class AgentRow(
    mcustomerid: Option[String],
    mbank: Option[String],
    mnumber: Option[String],
    mmobilenumber: Option[String],
    mlegalname: Option[String],
    mispendingagent: Option[Boolean],
    misconfirmedagent: Option[Boolean]
  )

  private case class DoobieAgent(row: AgentRow) extends Agent {
    override def agentId: String          = row.mcustomerid.getOrElse("")
    override def bankId: String           = row.mbank.getOrElse("")
    override def number: String           = row.mnumber.getOrElse("")
    override def legalName: String        = row.mlegalname.getOrElse("")
    override def mobileNumber: String     = row.mmobilenumber.getOrElse("")
    override def isConfirmedAgent: Boolean = row.misconfirmedagent.getOrElse(false)
    override def isPendingAgent: Boolean  = row.mispendingagent.getOrElse(false)
  }

  private val selectCols: Fragment =
    fr"SELECT mcustomerid, mbank, mnumber, mmobilenumber, mlegalname, mispendingagent, misconfirmedagent FROM mappedcustomer"

  // Reproduces MappedCustomerProvider.getOptionalParams: limit/offset, optional
  // updatedat range, and ordering by mlastokdate.
  private def optionalParamsFragment(queryParams: List[OBPQueryParam]): Fragment = {
    val fromDateFr = queryParams.collectFirst { case OBPFromDate(date) => fr"AND updatedat >= ${new Timestamp(date.getTime)}" }.getOrElse(Fragment.empty)
    val toDateFr   = queryParams.collectFirst { case OBPToDate(date)   => fr"AND updatedat <= ${new Timestamp(date.getTime)}" }.getOrElse(Fragment.empty)
    val orderFr    = queryParams.collectFirst {
      case OBPOrdering(_, OBPAscending)  => fr"ORDER BY mlastokdate ASC"
      case OBPOrdering(_, OBPDescending) => fr"ORDER BY mlastokdate DESC"
    }.getOrElse(Fragment.empty)
    val limitFr    = queryParams.collectFirst { case OBPLimit(v)  => fr"LIMIT $v"  }.getOrElse(Fragment.empty)
    val offsetFr   = queryParams.collectFirst { case OBPOffset(v) => fr"OFFSET $v" }.getOrElse(Fragment.empty)
    fromDateFr ++ toDateFr ++ orderFr ++ limitFr ++ offsetFr
  }

  override def getAgentsAtAllBanks(queryParams: List[OBPQueryParam]): Future[Box[List[Agent]]] = Future {
    Full(DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE 1 = 1" ++ optionalParamsFragment(queryParams))
        .query[AgentRow].to[List]).map(DoobieAgent(_)))
  }

  override def getAgentsFuture(bankId: BankId, queryParams: List[OBPQueryParam]): Future[Box[List[Agent]]] = Future {
    Full(DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE mbank = ${nn(bankId.value)}" ++ optionalParamsFragment(queryParams))
        .query[AgentRow].to[List]).map(DoobieAgent(_)))
  }

  override def getAgentsByAgentPhoneNumber(bankId: BankId, phoneNumber: String): Future[Box[List[Agent]]] = Future {
    Full(DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE mbank = ${nn(bankId.value)} AND mmobilenumber LIKE ${nn(phoneNumber)}")
        .query[AgentRow].to[List]).map(DoobieAgent(_)))
  }

  override def getAgentsByAgentLegalName(bankId: BankId, legalName: String): Future[Box[List[Agent]]] = Future {
    Full(DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE mbank = ${nn(bankId.value)} AND mlegalname LIKE ${nn(legalName)}")
        .query[AgentRow].to[List]).map(DoobieAgent(_)))
  }

  override def checkAgentNumberAvailable(bankId: BankId, agentNumber: String): Boolean = {
    val count = DoobieUtil.runQuery(
      fr"SELECT COUNT(*) FROM mappedcustomer WHERE mbank = ${nn(bankId.value)} AND mnumber = ${nn(agentNumber)}"
        .query[Int].unique)
    count match {
      case 0 => true
      case _ => false
    }
  }

  private def findByAgentId(agentId: String): Option[DoobieAgent] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE mcustomerid = ${nn(agentId)} LIMIT 1")
        .query[AgentRow].option).map(DoobieAgent(_))

  override def getAgentByAgentId(agentId: String): Box[Agent] =
    findByAgentId(agentId) match {
      case Some(a) => Full(a)
      case None    => Empty
    }

  override def getAgentByAgentIdFuture(agentId: String): Future[Box[Agent]] = Future {
    getAgentByAgentId(agentId)
  }

  override def getBankIdByAgentId(agentId: String): Box[String] =
    findByAgentId(agentId).map(_.bankId) match {
      case Some(b) => Full(b)
      case None    => Empty
    }

  override def getAgentByAgentNumber(bankId: BankId, agentNumber: String): Box[Agent] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE mnumber = ${nn(agentNumber)} AND mbank = ${nn(bankId.value)} LIMIT 1")
        .query[AgentRow].option).map(DoobieAgent(_)) match {
      case Some(a) => Full(a)
      case None    => Empty
    }

  override def getAgentByAgentNumberFuture(bankId: BankId, agentNumber: String): Future[Box[Agent]] =
    Future(getAgentByAgentNumber(bankId, agentNumber))

  override def createAgent(
    bankId: String,
    legalName: String,
    mobileNumber: String,
    agentNumber: String,
    callContext: Option[CallContext]
  ): Future[Box[Agent]] = Future {
    net.liftweb.util.Helpers.tryo {
      val newId = APIUtil.generateUUID()
      val ts = now
      DoobieUtil.runQuery(
        sql"""INSERT INTO mappedcustomer
                (mcustomerid, mbank, mlegalname, mmobilenumber, mnumber,
                 mispendingagent, misconfirmedagent, mcustomertype, mparentcustomerid,
                 createdat, updatedat)
              VALUES (${nn(newId)}, ${nn(bankId)}, ${nn(legalName)}, ${nn(mobileNumber)}, ${nn(agentNumber)},
                      true, false, ${"INDIVIDUAL"}, ${""},
                      $ts, $ts)""".update.run)
      DoobieAgent(AgentRow(
        Some(nn(newId)), Some(nn(bankId)), Some(nn(agentNumber)), Some(nn(mobileNumber)), Some(nn(legalName)),
        Some(true), Some(false)
      ))
    }
  }

  override def updateAgentStatus(
    agentId: String,
    isPendingAgent: Boolean,
    isConfirmedAgent: Boolean,
    callContext: Option[CallContext]
  ): Future[Box[Agent]] = Future {
    findByAgentId(agentId) match {
      case Some(existing) =>
        DoobieUtil.runQuery(
          sql"""UPDATE mappedcustomer
                SET mispendingagent = $isPendingAgent, misconfirmedagent = $isConfirmedAgent, updatedat = $now
                WHERE mcustomerid = ${nn(agentId)}""".update.run)
        Full(DoobieAgent(existing.row.copy(
          mispendingagent = Some(isPendingAgent),
          misconfirmedagent = Some(isConfirmedAgent)
        )))
      case None => Empty
    }
  }
}
