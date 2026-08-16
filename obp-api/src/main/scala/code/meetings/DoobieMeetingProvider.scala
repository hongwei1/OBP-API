package code.meetings

import code.api.util.{APIUtil, DoobieUtil, ErrorMessages}
import com.openbankproject.commons.model.{BankId, ContactDetails, Invitee, Meeting, MeetingKeys, MeetingPresent, User}
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Full}
import net.liftweb.util.Helpers.tryo

import java.sql.Timestamp
import java.util.Date
import scala.collection.immutable.List

object DoobieMeetingProvider extends MeetingProvider {

  // Tables: mappedmeeting + mappedmeetinginvitee (one-to-many via mmappedmeeting → mappedmeeting.id).
  // mcustomeruserid / mstaffuserid are BIGINT FKs to resourceuser.id; present resolves them to resourceuser.userid_.
  private case class MeetingRow(
    id: Long,
    mmeetingid: Option[String],
    mbankid: Option[String],
    mcustomeruserid: Option[Long],
    mstaffuserid: Option[Long],
    mproviderid: Option[String],
    mpurposeid: Option[String],
    msessionid: Option[String],
    mcustomertoken: Option[String],
    mstafftoken: Option[String],
    mwhen: Option[Timestamp],
    mcreatorname: Option[String],
    mcreatorphone: Option[String],
    mcreatoremail: Option[String]
  )

  private case class DoobieMeeting(
    meetingId: String,
    bankId: String,
    when: Date,
    providerId: String,
    purposeId: String,
    sessionId: String,
    customerToken: String,
    staffToken: String,
    creatorName: String,
    creatorPhone: String,
    creatorEmail: String,
    staffUserId: String,
    customerUserId: String,
    invitees: List[Invitee]
  ) extends Meeting {
    override def keys: MeetingKeys = MeetingKeys(sessionId, customerToken, staffToken)
    override def present: MeetingPresent = MeetingPresent(staffUserId = staffUserId, customerUserId = customerUserId)
    override def creator: ContactDetails = ContactDetails(creatorName, creatorPhone, creatorEmail)
  }

  private def nn(s: String): String = if (s == null) "" else s

  // Mirrors `mXxxUserId.foreign.map(_.userId).getOrElse("")` — resolve the ResourceUser FK to its userId UUID.
  private def resolveUserId(fk: Option[Long]): String =
    fk.flatMap { k =>
      DoobieUtil.runQuery(fr"SELECT userid_ FROM resourceuser WHERE id = $k LIMIT 1".query[Option[String]].option).flatten
    }.getOrElse("")

  private def readInvitees(meetingPk: Long): List[Invitee] =
    DoobieUtil.runQuery(
      fr"""SELECT mname, mphone, memail, mstatus FROM mappedmeetinginvitee
           WHERE mmappedmeeting = $meetingPk ORDER BY id ASC"""
        .query[(Option[String], Option[String], Option[String], Option[String])].to[List])
      .map { case (name, phone, email, status) =>
        Invitee(ContactDetails(name.getOrElse(""), phone.getOrElse(""), email.getOrElse("")), status.getOrElse(""))
      }

  private val selectCols: Fragment =
    fr"""SELECT id, mmeetingid, mbankid, mcustomeruserid, mstaffuserid, mproviderid, mpurposeid,
                msessionid, mcustomertoken, mstafftoken, mwhen, mcreatorname, mcreatorphone, mcreatoremail
         FROM mappedmeeting"""

  private def buildMeeting(row: MeetingRow): DoobieMeeting =
    DoobieMeeting(
      meetingId = row.mmeetingid.getOrElse(""),
      bankId = row.mbankid.getOrElse(""),
      when = row.mwhen.orNull,
      providerId = row.mproviderid.getOrElse(""),
      purposeId = row.mpurposeid.getOrElse(""),
      sessionId = row.msessionid.getOrElse(""),
      customerToken = row.mcustomertoken.getOrElse(""),
      staffToken = row.mstafftoken.getOrElse(""),
      creatorName = row.mcreatorname.getOrElse(""),
      creatorPhone = row.mcreatorphone.getOrElse(""),
      creatorEmail = row.mcreatoremail.getOrElse(""),
      staffUserId = resolveUserId(row.mstaffuserid),
      customerUserId = resolveUserId(row.mcustomeruserid),
      invitees = readInvitees(row.id)
    )

  override def getMeeting(bankId: BankId, user: User, meetingId: String): Box[Meeting] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE mbankid = ${nn(bankId.value)} AND mmeetingid = ${nn(meetingId)} ORDER BY mwhen DESC LIMIT 1")
        .query[MeetingRow].option) match {
      case Some(r) => Full(buildMeeting(r))
      case None    => net.liftweb.common.Empty
    }

  override def getMeetings(bankId: BankId, user: User): Box[List[Meeting]] =
    tryo {
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE mbankid = ${nn(bankId.value)} ORDER BY mwhen DESC").query[MeetingRow].to[List])
        .map(buildMeeting)
    }

  override def createMeeting(bankId: BankId, staffUser: User, customerUser: User, providerId: String, purposeId: String,
                             when: Date, sessionId: String, customerToken: String, staffToken: String,
                             creator: ContactDetails, invitees: List[Invitee]): Box[Meeting] =
    for {
      newMeeting <- tryo {
        val newMeetingId = APIUtil.generateUUID()
        val now = new Timestamp(System.currentTimeMillis())
        val whenTs = new Timestamp(when.getTime)
        // mStaffUserId is intentionally NOT set (the Mapper leaves it null).
        DoobieUtil.runQuery(
          sql"""INSERT INTO mappedmeeting
                  (mmeetingid, mbankid, mcustomeruserid, mproviderid, mpurposeid, mwhen, msessionid,
                   mcustomertoken, mstafftoken, mcreatorname, mcreatorphone, mcreatoremail, createdat, updatedat)
                VALUES ($newMeetingId, ${nn(bankId.value)}, ${customerUser.userPrimaryKey.value}, ${nn(providerId)},
                        ${nn(purposeId)}, $whenTs, ${nn(sessionId)}, ${nn(customerToken)}, ${nn(staffToken)},
                        ${nn(creator.name)}, ${nn(creator.phone)}, ${nn(creator.email)}, $now, $now)""".update.run)
        val pk = DoobieUtil.runQuery(
          fr"SELECT id FROM mappedmeeting WHERE mmeetingid = $newMeetingId LIMIT 1".query[Long].unique)
        (pk, newMeetingId)
      } ?~! ErrorMessages.CreateMeetingException
      _ <- tryo {
        invitees.foreach { invitee =>
          DoobieUtil.runQuery(
            sql"""INSERT INTO mappedmeetinginvitee (mmappedmeeting, mname, mphone, memail, mstatus)
                  VALUES (${newMeeting._1}, ${nn(invitee.contactDetails.name)}, ${nn(invitee.contactDetails.phone)},
                          ${nn(invitee.contactDetails.email)}, ${nn(invitee.status)})""".update.run)
        }
      } ?~! ErrorMessages.CreateMeetingInviteeException
    } yield {
      DoobieMeeting(
        meetingId = newMeeting._2,
        bankId = nn(bankId.value),
        when = when,
        providerId = nn(providerId),
        purposeId = nn(purposeId),
        sessionId = nn(sessionId),
        customerToken = nn(customerToken),
        staffToken = nn(staffToken),
        creatorName = nn(creator.name),
        creatorPhone = nn(creator.phone),
        creatorEmail = nn(creator.email),
        staffUserId = "",                       // mStaffUserId not set → present.staffUserId resolves to ""
        customerUserId = customerUser.userId,    // mCustomerUserId = customerUser → its userId
        invitees = invitees
      )
    }
}
