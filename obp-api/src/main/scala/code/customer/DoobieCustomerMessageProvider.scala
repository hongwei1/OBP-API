package code.customer

import code.api.util.{APIUtil, DoobieUtil}
import com.openbankproject.commons.model.{BankId, Customer, CustomerMessage, User}
import doobie._
import doobie.implicits._
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.tryo

import java.sql.Timestamp
import java.util.Date

object DoobieCustomerMessageProvider extends CustomerMessageProvider {

  private case class CustomerMessageRow(
    id: Option[Long],
    user_c: Option[Long],
    mmessageid: Option[String],
    mfromperson: Option[String],
    mfromdepartment: Option[String],
    mmessage: Option[String],
    mtransport: Option[String],
    createdat: Option[Timestamp],
    updatedat: Option[Timestamp],
    customer: Option[Long],
    bank: Option[String]
  )

  private case class DoobieCustomerMessage(row: CustomerMessageRow) extends CustomerMessage {
    override def messageId: String = row.mmessageid.getOrElse("")
    override def date: Date = row.createdat.map(ts => new Date(ts.getTime)).getOrElse(new Date())
    override def message: String = row.mmessage.getOrElse("")
    override def fromDepartment: String = row.mfromdepartment.getOrElse("")
    override def fromPerson: String = row.mfromperson.getOrElse("")
    override def transport: Option[String] = if (row.mtransport == null || row.mtransport.isEmpty || row.mtransport.get.isEmpty) None else row.mtransport
  }

  private def nn(s: String): String = if (s == null) "" else s
  private def now: Timestamp = new Timestamp(System.currentTimeMillis())

  private val selectCols: Fragment =
    fr"SELECT id, user_c, mmessageid, mfromperson, mfromdepartment, mmessage, mtransport, createdat, updatedat, customer, bank FROM mappedcustomermessage"

  override def getMessages(user: User, bankId: BankId): List[CustomerMessage] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE user_c = ${user.userPrimaryKey.value} AND bank = ${nn(bankId.value)} ORDER BY updatedat DESC")
        .query[CustomerMessageRow].to[List]).map(DoobieCustomerMessage(_))

  override def addMessage(user: User, bankId: BankId, message: String, fromDepartment: String, fromPerson: String): CustomerMessage = {
    val msgId = APIUtil.generateUUID()
    val ts = now
    DoobieUtil.runQuery(
      sql"""INSERT INTO mappedcustomermessage
             (mmessageid, mfromperson, mfromdepartment, mmessage, user_c, bank, createdat, updatedat)
             VALUES ($msgId, ${nn(fromPerson)}, ${nn(fromDepartment)}, ${nn(message)}, ${user.userPrimaryKey.value}, ${nn(bankId.value)}, $ts, $ts)""".update.run)
    DoobieCustomerMessage(CustomerMessageRow(
      None, Some(user.userPrimaryKey.value), Some(msgId), Some(nn(fromPerson)), Some(nn(fromDepartment)),
      Some(nn(message)), None, Some(ts), Some(ts), None, Some(nn(bankId.value))))
  }

  override def createCustomerMessage(customer: Customer, bankId: BankId, transport: String, message: String, fromDepartment: String, fromPerson: String): CustomerMessage = {
    val mappedCustomer = MappedCustomer.find(By(MappedCustomer.mCustomerId, customer.customerId)).head
    val customerId = mappedCustomer.primaryKeyField.get
    val msgId = APIUtil.generateUUID()
    val ts = now
    DoobieUtil.runQuery(
      sql"""INSERT INTO mappedcustomermessage
             (mmessageid, mfromperson, mfromdepartment, mmessage, mtransport, customer, bank, createdat, updatedat)
             VALUES ($msgId, ${nn(fromPerson)}, ${nn(fromDepartment)}, ${nn(message)}, ${nn(transport)}, $customerId, ${nn(bankId.value)}, $ts, $ts)""".update.run)
    DoobieCustomerMessage(CustomerMessageRow(
      None, None, Some(msgId), Some(nn(fromPerson)), Some(nn(fromDepartment)),
      Some(nn(message)), Some(nn(transport)), Some(ts), Some(ts), Some(customerId), Some(nn(bankId.value))))
  }

  override def getCustomerMessages(customer: Customer, bankId: BankId): List[CustomerMessage] = {
    val mappedCustomer = MappedCustomer.find(By(MappedCustomer.mCustomerId, customer.customerId)).head
    val customerId = mappedCustomer.primaryKeyField.get
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE customer = $customerId AND bank = ${nn(bankId.value)} ORDER BY updatedat DESC")
        .query[CustomerMessageRow].to[List]).map(DoobieCustomerMessage(_))
  }
}
