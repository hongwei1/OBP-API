package code.metadata.counterparties

import code.api.util.DoobieUtil
import com.openbankproject.commons.model.CounterpartyBespoke
import doobie._
import doobie.implicits._
import net.liftweb.util.Helpers.tryo

// DoobieCounterparties already handles bespokes via direct SQL in createCounterparty.
// This provider covers the legacy CounterpartyBespokes.vend interface (called only by the
// now-inactive MapperCounterparties) so the buildOne can point to Doobie.
object DoobieCounterpartyBespokesProvider extends CounterpartyBespokes {

  private case class Row(id: Long, counterpartyPk: Long, key: Option[String], value: Option[String])

  override def createCounterpartyBespokes(mapperCounterpartyPrimaryKey: Long,
                                          bespokes: List[CounterpartyBespoke]): List[MappedCounterpartyBespoke] = {
    bespokes.map { b =>
      val key = if (b.key == null) "" else b.key
      val value = if (b.value == null) "" else b.value
      DoobieUtil.runQuery(
        sql"""INSERT INTO mappedcounterpartybespoke (mcounterparty, mkey, mvaule)
              VALUES ($mapperCounterpartyPrimaryKey, $key, $value)""".update.run
      )
      // In-memory holder — no saveMe, callers only use mKey/mVaule field accessors.
      MappedCounterpartyBespoke.create
        .mCounterparty(mapperCounterpartyPrimaryKey)
        .mKey(key)
        .mVaule(value)
    }
  }

  override def getCounterpartyBespokesByCounterpartyId(mapperCounterpartyPrimaryKey: Long): List[MappedCounterpartyBespoke] = {
    DoobieUtil.runQuery(
      sql"""SELECT id, mcounterparty, mkey, mvaule
            FROM mappedcounterpartybespoke
            WHERE mcounterparty = $mapperCounterpartyPrimaryKey"""
        .query[Row].to[List]
    ).map { row =>
      MappedCounterpartyBespoke.create
        .mCounterparty(row.counterpartyPk)
        .mKey(row.key.getOrElse(""))
        .mVaule(row.value.getOrElse(""))
    }
  }
}
