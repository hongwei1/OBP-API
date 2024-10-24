package code.metrics

import java.util.{Calendar, Date}

import code.api.util.OBPQueryParam
import net.liftweb.util.SimpleInjector

object ConnectorMetricsProvider extends SimpleInjector {

  val metrics = new Inject(buildOne _) {}

  def buildOne: ConnectorMetricsProvider = ConnectorMetrics 

  /**
   * Returns a Date which is at the start of the day of the date
   * of the metric. Useful for implementing getAllGroupedByDay
   * @param metric
   * @return
   */
  def getMetricDay(metric : ConnectorMetric) : Date = {
    val cal = Calendar.getInstance()
    cal.setTime(metric.getDate())
    cal.set(Calendar.HOUR_OF_DAY,0)
    cal.set(Calendar.MINUTE,0)
    cal.set(Calendar.SECOND,0)
    cal.set(Calendar.MILLISECOND,0)
    cal.getTime
  }

}

trait ConnectorMetricsProvider {

  def saveConnectorMetric(connectorName: String, functionName: String, correlationId: String, date: Date, duration: Long): Unit
  def getAllConnectorMetrics(queryParams: List[OBPQueryParam]): List[ConnectorMetric]
  def bulkDeleteConnectorMetrics(): Boolean

}

trait ConnectorMetric {

  def getConnectorName(): String
  def getFunctionName(): String
  def getCorrelationId(): String
  def getDate(): Date
  def getDuration(): Long

}
