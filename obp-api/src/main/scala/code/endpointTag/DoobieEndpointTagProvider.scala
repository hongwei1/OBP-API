package code.endpointTag

import code.api.util.{APIUtil, DoobieUtil}
import com.openbankproject.commons.model.EndpointTagT
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo
import org.apache.commons.lang3.StringUtils

object DoobieEndpointTagProvider extends EndpointTagProvider {

  private case class TagRow(
    endpointtagid: Option[String],
    operationid: Option[String],
    tagname: Option[String],
    bankid: Option[String]
  )

  // Reproduce the Lift MappedString/MappedUUID getters exactly:
  //  - endpointTagId: Option(EndpointTagId.get)               (null -> None)
  //  - operationId:   OperationId.get                         (raw String)
  //  - tagName:       TagName.get                             (raw String)
  //  - bankId:        null or empty -> None else Some(value)
  private def toCommons(row: TagRow): EndpointTagCommons =
    EndpointTagCommons(
      endpointTagId = row.endpointtagid,
      operationId = row.operationid.getOrElse(""),
      tagName = row.tagname.getOrElse(""),
      bankId = row.bankid match {
        case Some(b) if b != null && b.nonEmpty => Some(b)
        case _ => None
      }
    )

  private def nn(s: String): String = if (s == null) "" else s

  private val selectCols: Fragment =
    fr"SELECT endpointtagid, operationid, tagname, bankid FROM endpointtag"

  private def findByEndpointTagId(endpointTagId: String): Option[TagRow] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE endpointtagid = ${nn(endpointTagId)} LIMIT 1")
        .query[TagRow].option)

  override def getById(endpointTagId: String): Box[EndpointTagT] =
    findByEndpointTagId(endpointTagId) match {
      case Some(row) => Full(toCommons(row))
      case None      => Empty
    }

  override def getByOperationId(operationId: String): Box[EndpointTagT] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE operationid = ${nn(operationId)} LIMIT 1")
        .query[TagRow].option) match {
      case Some(row) => Full(toCommons(row))
      case None      => Empty
    }

  override def getAllEndpointTags: List[EndpointTagT] =
    DoobieUtil.runQuery(selectCols.query[TagRow].to[List]).map(toCommons)

  override def createOrUpdate(endpointTag: EndpointTagT): Box[EndpointTagT] = {
    // to find exists endpointTag, if endpointTagId supplied, query by endpointTagId
    val existsRow: Option[TagRow] = endpointTag.endpointTagId match {
      case Some(id) if StringUtils.isNotBlank(id) => findByEndpointTagId(id)
      case _ => None
    }
    tryo {
      existsRow match {
        case Some(existing) =>
          val id = existing.endpointtagid.getOrElse("")
          // Note: Lift MappedEndpointTagProvider.createOrUpdate only persists
          // OperationId and TagName (BankId is never written) — preserved verbatim.
          DoobieUtil.runQuery(
            sql"""UPDATE endpointtag
                  SET operationid = ${nn(endpointTag.operationId)}, tagname = ${nn(endpointTag.tagName)}
                  WHERE endpointtagid = ${nn(id)}""".update.run)
          toCommons(existing.copy(
            operationid = Some(nn(endpointTag.operationId)),
            tagname = Some(nn(endpointTag.tagName))
          ))
        case None =>
          val newId = APIUtil.generateUUID()
          val createdAt = new java.sql.Timestamp(System.currentTimeMillis())
          // Lift EndpointTag.create only sets OperationId and TagName on save;
          // BankId stays at its default (null) so bankId getter returns None.
          DoobieUtil.runQuery(
            sql"""INSERT INTO endpointtag
                    (endpointtagid, operationid, tagname, createdat, updatedat)
                  VALUES ($newId, ${nn(endpointTag.operationId)}, ${nn(endpointTag.tagName)}, $createdAt, $createdAt)""".update.run)
          toCommons(TagRow(
            endpointtagid = Some(newId),
            operationid = Some(nn(endpointTag.operationId)),
            tagname = Some(nn(endpointTag.tagName)),
            bankid = None
          ))
      }
    }
  }

  override def delete(endpointTagId: String): Box[Boolean] =
    findByEndpointTagId(endpointTagId) match {
      case Some(_) =>
        Full(DoobieUtil.runQuery(
          sql"DELETE FROM endpointtag WHERE endpointtagid = ${nn(endpointTagId)}".update.run) >= 0)
      case None => Empty
    }
}
