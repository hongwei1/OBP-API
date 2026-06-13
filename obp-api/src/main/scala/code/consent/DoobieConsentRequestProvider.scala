package code.consent

import code.api.util.DoobieUtil
import code.model.Consumer
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import java.sql.Timestamp
import java.util.UUID.randomUUID

object DoobieConsentRequestProvider extends ConsentRequestProvider {

  // Table "consentrequest". The trait returns the concrete ConsentRequest, so the Lift class is reused
  // as an in-memory return holder (no second ConsentRequestTrait impl → no equality regression). All DB I/O via Doobie.
  private case class CrRow(
    consentrequestid: Option[String],
    payload: Option[String],
    consumerid: Option[String]
  )

  private def nn(s: String): String = if (s == null) "" else s

  private def toEntity(row: CrRow): ConsentRequest =
    ConsentRequest.create
      .ConsentRequestId(row.consentrequestid.getOrElse(""))
      .Payload(row.payload.getOrElse(""))
      .ConsumerId(row.consumerid.getOrElse(""))

  private val selectCols: Fragment =
    fr"""SELECT consentrequestid, payload, consumerid
         FROM consentrequest"""

  private def findRow(where: Fragment): Box[CrRow] =
    DoobieUtil.runQuery((selectCols ++ where ++ fr"LIMIT 1").query[CrRow].option) match {
      case Some(r) => Full(r)
      case None    => Empty
    }

  override def getConsentRequestById(consentRequestId: String): Box[ConsentRequest] =
    findRow(fr"WHERE consentrequestid = ${nn(consentRequestId)}").map(toEntity)

  override def createConsentRequest(consumer: Option[Consumer], payload: Option[String]): Box[ConsentRequest] =
    tryo {
      // Mirrors the Mapper: ConsentRequestId is a MappedUUID (auto-generated), ConsumerId derives from the consumer.
      val newId = randomUUID().toString
      val consumerId = nn(consumer.map(_.consumerId.get).orNull)
      val payloadValue = payload.getOrElse("")
      val now = new Timestamp(System.currentTimeMillis())
      DoobieUtil.runQuery(
        sql"""INSERT INTO consentrequest
                (consentrequestid, payload, consumerid, createdat, updatedat)
              VALUES ($newId, $payloadValue, $consumerId, $now, $now)""".update.run)
      toEntity(CrRow(Some(newId), Some(payloadValue), Some(consumerId)))
    }
}
