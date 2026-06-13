package code.users

import code.api.util.{DoobieUtil, SecureRandomUtil}
import com.openbankproject.commons.model.BankId
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers
import net.liftweb.util.Helpers.tryo

import java.sql.Timestamp
import java.util.UUID.randomUUID
import scala.collection.immutable.List

object DoobieUserInvitationProvider extends UserInvitationProvider {

  // Table "userinvitation". The trait returns the concrete UserInvitation, so the Lift class is reused
  // as an in-memory return holder (no second UserInvitationTrait impl → no equality regression).
  private case class UiRow(
    userinvitationid: Option[String],
    bankid: Option[String],
    firstname: Option[String],
    lastname: Option[String],
    email: Option[String],
    company: Option[String],
    country: Option[String],
    status: Option[String],
    purpose: Option[String],
    secretkey: Option[Long]
  )

  private def nn(s: String): String = if (s == null) "" else s

  private def toEntity(row: UiRow): UserInvitation =
    UserInvitation.create
      .UserInvitationId(row.userinvitationid.getOrElse(""))
      .BankId(row.bankid.getOrElse(""))
      .FirstName(row.firstname.getOrElse(""))
      .LastName(row.lastname.getOrElse(""))
      .Email(row.email.getOrElse(""))
      .Company(row.company.getOrElse(""))
      .Country(row.country.getOrElse(""))
      .Status(row.status.getOrElse(""))
      .Purpose(row.purpose.getOrElse(""))
      .SecretKey(row.secretkey.getOrElse(0L))

  private val selectCols: Fragment =
    fr"""SELECT userinvitationid, bankid, firstname, lastname, email, company, country, status, purpose, secretkey
         FROM userinvitation"""

  private def findRow(where: Fragment): Box[UiRow] =
    DoobieUtil.runQuery((selectCols ++ where ++ fr"LIMIT 1").query[UiRow].option) match {
      case Some(r) => Full(r)
      case None    => Empty
    }

  override def createUserInvitation(bankId: BankId, firstName: String, lastName: String, email: String,
                                    company: String, country: String, purpose: String): Box[UserInvitation] =
    tryo {
      val newId = randomUUID().toString
      val secretKey = SecureRandomUtil.csprng.nextLong()
      val now = new Timestamp(System.currentTimeMillis())
      DoobieUtil.runQuery(
        sql"""INSERT INTO userinvitation
                (userinvitationid, bankid, firstname, lastname, email, company, country, status, purpose, secretkey, createdat, updatedat)
              VALUES ($newId, ${nn(bankId.value)}, ${nn(firstName)}, ${nn(lastName)}, ${nn(email)}, ${nn(company)},
                      ${nn(country)}, 'CREATED', ${nn(purpose)}, $secretKey, $now, $now)""".update.run)
      toEntity(UiRow(Some(newId), Some(nn(bankId.value)), Some(nn(firstName)), Some(nn(lastName)), Some(nn(email)),
        Some(nn(company)), Some(nn(country)), Some("CREATED"), Some(nn(purpose)), Some(secretKey)))
    }

  override def getUserInvitationBySecretLink(secretLink: Long): Box[UserInvitation] =
    findRow(fr"WHERE secretkey = $secretLink").map(toEntity)

  override def updateStatusOfUserInvitation(userInvitationId: String, status: String): Box[Boolean] =
    tryo {
      findRow(fr"WHERE userinvitationid = ${nn(userInvitationId)}") match {
        case Full(_) =>
          DoobieUtil.runQuery(
            sql"UPDATE userinvitation SET status = ${nn(status)} WHERE userinvitationid = ${nn(userInvitationId)}".update.run)
          true
        case _ => false
      }
    }

  override def scrambleUserInvitation(userInvitationId: String): Box[Boolean] =
    tryo {
      findRow(fr"WHERE userinvitationid = ${nn(userInvitationId)}") match {
        case Full(inv) =>
          // Replace each field with a random string of the same length, mark DELETED (mirrors the Mapper).
          val email = Helpers.randomString(10) + "@example.com"
          val firstName = Helpers.randomString(inv.firstname.getOrElse("").length)
          val lastName = Helpers.randomString(inv.lastname.getOrElse("").length)
          val company = Helpers.randomString(inv.company.getOrElse("").length)
          val country = Helpers.randomString(inv.country.getOrElse("").length)
          val purpose = Helpers.randomString(inv.purpose.getOrElse("").length)
          DoobieUtil.runQuery(
            sql"""UPDATE userinvitation
                  SET email = $email, firstname = $firstName, lastname = $lastName, company = $company,
                      country = $country, purpose = $purpose, status = 'DELETED'
                  WHERE userinvitationid = ${nn(userInvitationId)}""".update.run)
          true
        case _ => false
      }
    }

  override def getUserInvitation(bankId: BankId, secretLink: Long): Box[UserInvitation] =
    findRow(fr"WHERE bankid = ${nn(bankId.value)} AND secretkey = $secretLink").map(toEntity)

  override def getUserInvitations(bankId: BankId): Box[List[UserInvitation]] =
    tryo {
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE bankid = ${nn(bankId.value)}").query[UiRow].to[List]).map(toEntity)
    }
}
