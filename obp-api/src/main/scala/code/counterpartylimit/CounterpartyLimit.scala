package code.counterpartylimit

import com.openbankproject.commons.model.CounterpartyLimitTrait
import net.liftweb.util.SimpleInjector
import net.liftweb.common.Box
import scala.concurrent.Future

object CounterpartyLimitProvider extends SimpleInjector {
  val counterpartyLimit = new Inject(buildOne _) {}
  // Phase 8 of the Lift Mapper -> Doobie migration: counterparty-limit access now goes through Doobie SQL.
  // MappedCounterpartyLimitProvider / CounterpartyLimit entity stay for schema during the coexistence phase.
  def buildOne: CounterpartyLimitProviderTrait = DoobieCounterpartyLimitProvider
}

trait CounterpartyLimitProviderTrait {
  def getCounterpartyLimit(
    bankId: String,
    accountId: String,
    viewId: String,
    counterpartyId: String
  ): Future[Box[CounterpartyLimitTrait]]
  
  def deleteCounterpartyLimit(
    bankId: String,
    accountId: String,
    viewId: String,
    counterpartyId: String
  ): Future[Box[Boolean]]
  
  def createOrUpdateCounterpartyLimit(
    bankId: String,
    accountId: String,
    viewId: String,
    counterpartyId: String,
    currency: String,
    maxSingleAmount: BigDecimal,
    maxMonthlyAmount: BigDecimal,
    maxNumberOfMonthlyTransactions: Int,
    maxYearlyAmount: BigDecimal,
    maxNumberOfYearlyTransactions: Int,
    maxTotalAmount: BigDecimal,
    maxNumberOfTransactions: Int
  ): Future[Box[CounterpartyLimitTrait]]
}

