package code.examplething

import code.api.util.DoobieUtil
import com.openbankproject.commons.model.BankId
import doobie._
import doobie.implicits._

import scala.collection.immutable.List

object DoobieThingProvider extends ThingProvider {

  // Table "mappedthing" (class MappedThing lowercased; no dbTableName override).
  // Lift derives column names from the field-object names lowercased, keeping the
  // trailing underscore: bankId_ -> bankid_, name_ -> name_, thingId_ -> thingid_,
  // fooSomething_ -> foosomething_, barSomething_ -> barsomething_.
  private case class ThingRow(
    bankid_ : Option[String],
    name_ : Option[String],
    thingid_ : Option[String],
    foosomething_ : Option[String],
    barsomething_ : Option[String]
  )

  private case class DoobieThing(row: ThingRow) extends Thing {
    override def thingId: ThingId = ThingId(row.thingid_.getOrElse(""))
    override def something: String = row.name_.getOrElse("")
    override def foo: Foo = new Foo {
      override def fooSomething: String = row.foosomething_.getOrElse("")
    }
    override def bar: Bar = new Bar {
      override def barSomething: String = row.barsomething_.getOrElse("")
    }
  }

  private def nn(s: String): String = if (s == null) "" else s

  private val selectCols: Fragment =
    fr"SELECT bankid_, name_, thingid_, foosomething_, barsomething_ FROM mappedthing"

  override protected def getThingFromProvider(thingId: ThingId): Option[Thing] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE thingid_ = ${nn(thingId.value)} LIMIT 1").query[ThingRow].option)
      .map(DoobieThing(_))

  override protected def getThingsFromProvider(bank: BankId): Option[List[Thing]] =
    Some(
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE bankid_ = ${nn(bank.value)}").query[ThingRow].to[List])
        .map(DoobieThing(_)))
}
