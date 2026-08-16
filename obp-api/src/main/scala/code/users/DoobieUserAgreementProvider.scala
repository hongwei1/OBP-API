package code.users

import code.api.util.{DoobieUtil, HashUtil}
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}

import java.util.UUID.randomUUID
import java.util.Date

object DoobieUserAgreementProvider extends UserAgreementProvider {

  // Table "useragreement": id, date_c (DATE reserved → _c), agreementhash, agreementtype, agreementtext,
  // userid, createdat, updatedat, useragreementid. The concrete-returning trait reuses the Lift UserAgreement
  // class as an in-memory holder (no second UserAgreementTrait impl, so no equality regression).
  private case class UaRow(
    useragreementid: Option[String],
    userid: Option[String],
    agreementtype: Option[String],
    agreementtext: Option[String],
    agreementhash: Option[String],
    date_c: Option[java.sql.Date]
  )

  private def nn(s: String): String = if (s == null) "" else s

  private def toEntity(row: UaRow): UserAgreement =
    UserAgreement.create
      .UserAgreementId(row.useragreementid.getOrElse(""))
      .UserId(row.userid.getOrElse(""))
      .AgreementType(row.agreementtype.getOrElse(""))
      .AgreementText(row.agreementtext.getOrElse(""))
      .AgreementHash(row.agreementhash.getOrElse(""))
      .Date(row.date_c.getOrElse(new java.sql.Date(0L)))

  private val selectCols: Fragment =
    fr"SELECT useragreementid, userid, agreementtype, agreementtext, agreementhash, date_c FROM useragreement"

  override def createUserAgreement(userId: String, agreementType: String, agreementText: String): Box[UserAgreement] = {
    val newId = randomUUID().toString
    val hash = HashUtil.Sha256Hash(agreementText)
    val now = new Date()
    val sqlDate = new java.sql.Date(now.getTime)
    val ts = new java.sql.Timestamp(now.getTime)
    DoobieUtil.runQuery(
      sql"""INSERT INTO useragreement
              (useragreementid, userid, agreementtype, agreementtext, agreementhash, date_c, createdat, updatedat)
            VALUES ($newId, ${nn(userId)}, ${nn(agreementType)}, ${nn(agreementText)}, ${nn(hash)}, $sqlDate, $ts, $ts)""".update.run)
    Full(toEntity(UaRow(Some(newId), Some(nn(userId)), Some(nn(agreementType)), Some(nn(agreementText)), Some(nn(hash)), Some(sqlDate))))
  }

  override def getLastUserAgreement(userId: String, agreementType: String): Box[UserAgreement] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE userid = ${nn(userId)} AND agreementtype = ${nn(agreementType)} ORDER BY date_c DESC, id DESC LIMIT 1")
        .query[UaRow].option) match {
      case Some(r) => Full(toEntity(r))
      case None    => Empty
    }

  def findAllByUserIds(userIds: List[String]): Map[String, List[UserAgreement]] =
    if (userIds.isEmpty) Map.empty
    else {
      val inList = userIds.map(id => fr"$id").reduceLeft((a, b) => a ++ fr"," ++ b)
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE userid IN (" ++ inList ++ fr")")
          .query[UaRow].to[List]
      ).map(toEntity)
       .groupBy(_.userId)
       .map { case (uid, all) =>
         uid -> all.groupBy(_.agreementType)
           .values
           .flatMap(_.sortBy(_.Date.get)(Ordering[Date].reverse).headOption)
           .toList
       }
    }
}
