package code.signingbaskets

import code.api.berlin.group.ConstantsBG
import code.api.util.{APIUtil, DoobieUtil}
import com.openbankproject.commons.model.{SigningBasketContent, SigningBasketTrait}
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.List

object DoobieSigningBasketProvider extends SigningBasketProvider {

  // Tables: signingbasket (basketid, status), signingbasketpayment (basketid, paymentid),
  // signingbasketconsent (basketid, consentid). All entities use IdPK only (no created/updated columns).
  private case class BasketRow(basketid: Option[String], status: Option[String])

  private case class DoobieSigningBasket(row: BasketRow) extends SigningBasketTrait {
    override def basketId: String = row.basketid.getOrElse("")
    override def status: String = row.status.getOrElse("")
  }

  private def nn(s: String): String = if (s == null) "" else s

  private val selectBasket: Fragment = fr"SELECT basketid, status FROM signingbasket"

  private def findBasketRow(basketId: String): Box[BasketRow] =
    DoobieUtil.runQuery(
      (selectBasket ++ fr"WHERE basketid = ${nn(basketId)} LIMIT 1").query[BasketRow].option) match {
      case Some(r) => Full(r)
      case None    => Empty
    }

  private def findPaymentIds(basketId: String): Option[List[String]] =
    DoobieUtil.runQuery(
      fr"SELECT paymentid FROM signingbasketpayment WHERE basketid = ${nn(basketId)}".query[Option[String]].to[List])
      .map(_.getOrElse("")) match {
      case Nil          => None
      case head :: tail => Some(head :: tail)
    }

  private def findConsentIds(basketId: String): Option[List[String]] =
    DoobieUtil.runQuery(
      fr"SELECT consentid FROM signingbasketconsent WHERE basketid = ${nn(basketId)}".query[Option[String]].to[List])
      .map(_.getOrElse("")) match {
      case Nil          => None
      case head :: tail => Some(head :: tail)
    }

  override def getSigningBaskets(): List[SigningBasketTrait] =
    DoobieUtil.runQuery(selectBasket.query[BasketRow].to[List]).map(DoobieSigningBasket(_))

  override def getSigningBasketByBasketId(entityId: String): Box[SigningBasketContent] = {
    val basket = findBasketRow(entityId).map(DoobieSigningBasket(_))
    val payments = findPaymentIds(entityId)
    val consents = findConsentIds(entityId)
    basket.map(i => SigningBasketContent(basket = i, payments = payments, consents = consents))
  }

  override def saveSigningBasketStatus(entityId: String, status: String): Box[SigningBasketContent] = {
    val basket = findBasketRow(entityId).map { row =>
      DoobieUtil.runQuery(
        sql"UPDATE signingbasket SET status = ${nn(status)} WHERE basketid = ${nn(entityId)}".update.run)
      DoobieSigningBasket(row.copy(status = Some(nn(status))))
    }
    val payments = findPaymentIds(entityId)
    val consents = findConsentIds(entityId)
    basket.map(i => SigningBasketContent(basket = i, payments = payments, consents = consents))
  }

  override def createSigningBasket(paymentIds: Option[List[String]],
                                   consentIds: Option[List[String]]): Box[SigningBasketTrait] =
    tryo {
      // BasketId is a MappedUUID in Lift (auto-generated default); generate it here.
      val basketId = APIUtil.generateUUID()
      val status = ConstantsBG.SigningBasketsStatus.RCVD.toString
      DoobieUtil.runQuery(
        sql"INSERT INTO signingbasket (basketid, status) VALUES ($basketId, $status)".update.run)
      paymentIds.getOrElse(Nil).foreach { paymentId =>
        DoobieUtil.runQuery(
          sql"INSERT INTO signingbasketpayment (basketid, paymentid) VALUES ($basketId, ${nn(paymentId)})".update.run)
      }
      consentIds.getOrElse(Nil).foreach { consentId =>
        DoobieUtil.runQuery(
          sql"INSERT INTO signingbasketconsent (basketid, consentid) VALUES ($basketId, ${nn(consentId)})".update.run)
      }
      DoobieSigningBasket(BasketRow(Some(basketId), Some(status)))
    }

  override def deleteSigningBasket(id: String): Box[Boolean] =
    findBasketRow(id).map { _ =>
      // Mirrors the Mapper: a "delete" is a soft cancel (Status = CANC).
      DoobieUtil.runQuery(
        sql"UPDATE signingbasket SET status = ${ConstantsBG.SigningBasketsStatus.CANC.toString} WHERE basketid = ${nn(id)}".update.run) >= 0
    }
}
