package code.standingorders

import java.util.Date

import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector
import com.openbankproject.commons.model.StandingOrderTrait
import scala.math.BigDecimal


object StandingOrders extends SimpleInjector {
  val provider = new Inject(buildOne _) {}
  // Phase 7 of the Lift Mapper -> Doobie migration: standing-order access now goes through Doobie SQL.
  // MappedStandingOrderProvider / StandingOrder entity stay for schema during the coexistence phase.
  def buildOne: StandingOrderProvider = DoobieStandingOrderProvider
}

trait StandingOrderProvider {
  def createStandingOrder(bankId: String,
                          accountId: String,
                          customerId: String,
                          userId: String,
                          counterpartyId: String,
                          amountValue: BigDecimal,
                          amountCurrency: String,
                          whenFrequency: String,
                          whenDetail: String,
                          dateSigned: Date,
                          dateStarts: Date,
                          dateExpires: Option[Date]
                       ): Box[StandingOrderTrait]
  def getStandingOrdersByCustomer(customerId: String) : List[StandingOrderTrait]
  def getStandingOrdersByUser(userId: String) : List[StandingOrderTrait]
}

