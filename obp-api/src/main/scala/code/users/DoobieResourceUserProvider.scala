package code.users

import code.api.util.DoobieUtil
import com.openbankproject.commons.model.{User, UserPrimaryKey}
import doobie._
import doobie.implicits._

import java.sql.{Date => SqlDate}
import java.util.Date

/**
 * Doobie-backed read access for the `resourceuser` table, returning a
 * [[DoobieResourceUser]] that implements the commons `User` trait.
 *
 * Only the lookups that already return `User` (not the concrete Lift
 * `ResourceUser`) are routed here — the user-resolution hot path hit on every
 * authenticated request. Methods that return the concrete `ResourceUser`
 * (mutated by callers) or the unsaved/save APIs stay on Lift Mapper during
 * coexistence; both paths read the same table.
 *
 * The accessor null/empty handling mirrors the ResourceUser Mapper accessors
 * exactly (null → None, empty string → None for the id-ish fields, etc.).
 */
object DoobieResourceUserProvider {

  case class RuRow(
    id: Option[Long],
    userid: Option[String],
    provider: Option[String],
    providerid: Option[String],
    name: Option[String],
    email: Option[String],
    createdbyconsentid: Option[String],
    createdbyuserinvitationid: Option[String],
    isdeleted: Option[Boolean],
    lastmarketingdate: Option[SqlDate],
    isnaturalperson: Option[Boolean],
    principaluserid: Option[String],
    lastusedlocale: Option[String]
  )

  case class DoobieResourceUser(row: RuRow) extends User {
    override def userPrimaryKey: UserPrimaryKey = UserPrimaryKey(row.id.getOrElse(0L))
    override def userId: String                 = row.userid.getOrElse("")
    override def idGivenByProvider: String       = row.providerid.getOrElse("")
    override def provider: String               = row.provider.getOrElse("")
    override def emailAddress: String           = row.email.getOrElse("")
    override def name: String                   = row.name.getOrElse("")
    override def createdByConsentId: Option[String]         = row.createdbyconsentid.filter(_.nonEmpty)
    override def createdByUserInvitationId: Option[String]  = row.createdbyuserinvitationid.filter(_.nonEmpty)
    override def isDeleted: Option[Boolean]                 = row.isdeleted
    override def lastMarketingAgreementSignedDate: Option[Date] = row.lastmarketingdate.map(d => new Date(d.getTime))
    override def lastUsedLocale: Option[String]            = row.lastusedlocale
    override def isNaturalPerson: Boolean                  = row.isnaturalperson.getOrElse(true)
    override def principalUserIdOption: Option[String]     = row.principaluserid.filter(_.nonEmpty)
  }

  private val selectCols: Fragment =
    fr"""SELECT id, userid_, provider_, providerid, name_, email, createdbyconsentid,
                createdbyuserinvitationid, isdeleted, lastmarketingagreementsigneddate,
                isnaturalperson, principaluserid, lastusedlocale
         FROM resourceuser"""

  private def findOne(where: Fragment): Option[DoobieResourceUser] =
    DoobieUtil.runQuery((selectCols ++ where ++ fr"LIMIT 1").query[RuRow].option).map(DoobieResourceUser(_))

  def findByResourceUserId(id: Long): Option[DoobieResourceUser] =
    findOne(fr"WHERE id = $id")

  def findByUserId(userId: String): Option[DoobieResourceUser] =
    findOne(fr"WHERE userid_ = $userId")

  def findByProviderId(provider: String, idGivenByProvider: String): Option[DoobieResourceUser] =
    findOne(fr"WHERE provider_ = $provider AND providerid = $idGivenByProvider")

  def findByProviderAndUsername(provider: String, userName: String): Option[DoobieResourceUser] =
    findOne(fr"WHERE provider_ = $provider AND name_ = $userName")

  def findAllByUserIds(userIds: List[String]): List[DoobieResourceUser] = {
    if (userIds.isEmpty) Nil
    else {
      val inList = userIds.map(id => fr"$id").reduceLeft((a, b) => a ++ fr"," ++ b)
      DoobieUtil.runQuery((selectCols ++ fr"WHERE userid_ IN (" ++ inList ++ fr")").query[RuRow].to[List])
        .map(DoobieResourceUser(_))
    }
  }
}
