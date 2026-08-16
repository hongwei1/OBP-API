package code.customerattribute

import code.api.util.{APIUtil, DoobieUtil}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.dto.CustomerAndAttribute
import com.openbankproject.commons.model.enums.CustomerAttributeType
import com.openbankproject.commons.model.{BankId, Customer, CustomerAttribute, CustomerId}
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.List
import scala.concurrent.Future

object DoobieCustomerAttributeProvider extends CustomerAttributeProvider {

  private case class AttrRow(
    mbankidid: Option[String],
    mcustomerid: Option[String],
    mcustomerattributeid: Option[String],
    mname: Option[String],
    mtype: Option[String],
    mvalue: Option[String]
  )

  private case class DoobieCustomerAttribute(row: AttrRow) extends CustomerAttribute {
    override def bankId: BankId               = BankId(row.mbankidid.getOrElse(""))
    override def customerId: CustomerId       = CustomerId(row.mcustomerid.getOrElse(""))
    override def customerAttributeId: String  = row.mcustomerattributeid.getOrElse("")
    override def name: String                 = row.mname.getOrElse("")
    override def attributeType: CustomerAttributeType.Value =
      CustomerAttributeType.withName(row.mtype.getOrElse("STRING"))
    override def value: String = row.mvalue.getOrElse("")
  }

  private def nn(s: String): String = if (s == null) "" else s

  private val selectCols: Fragment =
    fr"""SELECT mbankidid, mcustomerid, mcustomerattributeid, mname, mtype, mvalue
         FROM mappedcustomerattribute"""

  private def findById(id: String): Option[DoobieCustomerAttribute] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE mcustomerattributeid = ${nn(id)} LIMIT 1")
        .query[AttrRow].option).map(DoobieCustomerAttribute(_))

  override def getCustomerAttributesFromProvider(customerId: CustomerId): Future[Box[List[CustomerAttribute]]] = Future {
    tryo {
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE mcustomerid = ${nn(customerId.value)}")
          .query[AttrRow].to[List]).map(DoobieCustomerAttribute(_))
    }
  }

  override def getCustomerAttributes(bankId: BankId, customerId: CustomerId): Future[Box[List[CustomerAttribute]]] = Future {
    tryo {
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE mbankidid = ${nn(bankId.value)} AND mcustomerid = ${nn(customerId.value)}")
          .query[AttrRow].to[List]).map(DoobieCustomerAttribute(_))
    }
  }

  override def getCustomerIdsByAttributeNameValues(bankId: BankId, params: Map[String, List[String]]): Future[Box[List[String]]] = Future {
    tryo {
      if (params.isEmpty) {
        DoobieUtil.runQuery(
          fr"SELECT mcustomerid FROM mappedcustomerattribute WHERE mbankidid = ${nn(bankId.value)}"
            .query[Option[String]].to[List]).flatten
      } else {
        // Replicate Lift's BySql OR logic: one row per matching attribute, customer IDs may repeat.
        // The callers (getCustomersByAttributes endpoint) iterate over the returned IDs, so duplicates
        // produce duplicate entries in the response — matching the original Lift behaviour.
        val paramList = params.toList
        val conditions: List[Fragment] = paramList.flatMap { case (name, values) =>
          if (values.size == 1)
            List(fr"(mname = ${nn(name)} AND mvalue = ${nn(values.head)})")
          else {
            val inFr = values.map(v => fr0"${nn(v)}").reduce((a, b) => a ++ fr", " ++ b)
            List(fr"(mname = ${nn(name)} AND mvalue IN (" ++ inFr ++ fr"))")
          }
        }
        val orFr = conditions.reduce((a, b) => a ++ fr" OR " ++ b)
        DoobieUtil.runQuery(
          (fr"SELECT mcustomerid FROM mappedcustomerattribute WHERE mbankidid = ${nn(bankId.value)} AND (" ++ orFr ++ fr")")
            .query[Option[String]].to[List]).flatten
      }
    }
  }

  override def getCustomerAttributesForCustomers(customers: List[Customer]): Future[Box[List[CustomerAndAttribute]]] = Future {
    tryo {
      customers.map { customer =>
        val attrs = DoobieUtil.runQuery(
          (selectCols ++ fr"WHERE mbankidid = ${nn(customer.bankId)} AND mcustomerid = ${nn(customer.customerId)}")
            .query[AttrRow].to[List]).map(DoobieCustomerAttribute(_))
        CustomerAndAttribute(customer, attrs)
      }
    }
  }

  override def getCustomerAttributeById(customerAttributeId: String): Future[Box[CustomerAttribute]] = Future {
    findById(customerAttributeId) match {
      case Some(a) => Full(a)
      case None    => Empty
    }
  }

  override def createOrUpdateCustomerAttribute(bankId: BankId,
                                               customerId: CustomerId,
                                               customerAttributeId: Option[String],
                                               name: String,
                                               attributeType: CustomerAttributeType.Value,
                                               value: String): Future[Box[CustomerAttribute]] = {
    customerAttributeId match {
      case Some(id) => Future {
        findById(id) match {
          case Some(_) =>
            tryo {
              DoobieUtil.runQuery(
                sql"""UPDATE mappedcustomerattribute
                      SET mbankidid = ${nn(bankId.value)}, mcustomerid = ${nn(customerId.value)},
                          mname = ${nn(name)}, mtype = ${attributeType.toString}, mvalue = ${nn(value)}
                      WHERE mcustomerattributeid = ${nn(id)}""".update.run)
              DoobieCustomerAttribute(AttrRow(
                Some(nn(bankId.value)), Some(nn(customerId.value)),
                Some(nn(id)), Some(nn(name)), Some(attributeType.toString), Some(nn(value))
              ))
            }
          case None => Empty
        }
      }
      case None => Future {
        tryo {
          val newId = APIUtil.generateUUID()
          DoobieUtil.runQuery(
            sql"""INSERT INTO mappedcustomerattribute
                  (mbankidid, mcustomerid, mcustomerattributeid, mname, mtype, mvalue)
                  VALUES (${nn(bankId.value)}, ${nn(customerId.value)}, $newId, ${nn(name)},
                          ${attributeType.toString}, ${nn(value)})""".update.run)
          DoobieCustomerAttribute(AttrRow(
            Some(nn(bankId.value)), Some(nn(customerId.value)),
            Some(newId), Some(nn(name)), Some(attributeType.toString), Some(nn(value))
          ))
        }
      }
    }
  }

  override def createCustomerAttributes(bankId: BankId,
                                        customerId: CustomerId,
                                        customerAttributes: List[CustomerAttribute]): Future[Box[List[CustomerAttribute]]] = Future {
    tryo {
      customerAttributes.map { attr =>
        val newId = APIUtil.generateUUID()
        DoobieUtil.runQuery(
          sql"""INSERT INTO mappedcustomerattribute
                (mbankidid, mcustomerid, mcustomerattributeid, mname, mtype, mvalue)
                VALUES (${nn(bankId.value)}, ${nn(customerId.value)}, $newId,
                        ${nn(attr.name)}, ${attr.attributeType.toString}, ${nn(attr.value)})""".update.run)
        DoobieCustomerAttribute(AttrRow(
          Some(nn(bankId.value)), Some(nn(customerId.value)),
          Some(newId), Some(nn(attr.name)), Some(attr.attributeType.toString), Some(nn(attr.value))
        ))
      }
    }
  }

  override def deleteCustomerAttribute(customerAttributeId: String): Future[Box[Boolean]] = Future {
    Full(DoobieUtil.runQuery(
      sql"DELETE FROM mappedcustomerattribute WHERE mcustomerattributeid = ${nn(customerAttributeId)}".update.run) > 0)
  }
}
