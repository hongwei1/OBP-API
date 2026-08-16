package code.metrics

import java.util.Date
import java.util.UUID.randomUUID
import java.sql.Timestamp

import code.api.cache.Caching
import code.api.util._
import code.api.util.DoobieUtil
import com.tesobe.CacheKeyFromArguments
import doobie._
import doobie.implicits._
import scala.concurrent.duration._

object ConnectorMetrics extends ConnectorMetricsProvider {

  val cachedAllConnectorMetrics = APIUtil.getPropsValue(s"ConnectorMetrics.cache.ttl.seconds.getAllConnectorMetrics", "7").toInt

  override def saveConnectorMetric(connectorName: String, functionName: String, correlationId: String, date: Date, duration: Long,
                                   requestParams: String, isSuccessful: Boolean, apiInstanceId: String): Unit = {
    ConnectorMetricBatchWriter.enqueue(
      ConnectorMetricBatchWriter.ConnectorMetricRow(
        connectorName = connectorName,
        functionName = functionName,
        correlationId = correlationId,
        date = date,
        duration = duration,
        requestParams = requestParams,
        isSuccessful = isSuccessful,
        apiInstanceId = apiInstanceId
      )
    )
  }

  private case class MetricRow(
    connectorName: String,
    functionName: String,
    correlationId: String,
    date: Option[Timestamp],
    duration: Long,
    requestParams: String,
    isSuccessful: Boolean,
    apiInstanceId: String
  )

  private case class DoobieConnectorMetric(
    connectorName: String,
    functionName: String,
    correlationId: String,
    date: Date,
    duration: Long,
    requestParams: String,
    isSuccessful: Boolean,
    apiInstanceId: String
  ) extends ConnectorMetric {
    def getConnectorName(): String = connectorName
    def getFunctionName(): String  = functionName
    def getCorrelationId(): String = correlationId
    def getDate(): Date            = date
    def getDuration(): Long        = duration
    def getRequestParams(): String = requestParams
    def getIsSuccessful(): Boolean = isSuccessful
    def getApiInstanceId(): String = apiInstanceId
  }

  override def getAllConnectorMetrics(queryParams: List[OBPQueryParam]): List[ConnectorMetric] = {
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(cachedAllConnectorMetrics.days) {

        val conditions: List[Fragment] = List(
          queryParams.collectFirst { case OBPFromDate(d)       => fr"date >= ${new Timestamp(d.getTime)}" },
          queryParams.collectFirst { case OBPToDate(d)         => fr"date <= ${new Timestamp(d.getTime)}" },
          queryParams.collectFirst { case OBPCorrelationId(v)  => fr"correlationid = $v" },
          queryParams.collectFirst { case OBPFunctionName(v)   => fr"functionname = $v" },
          queryParams.collectFirst { case OBPConnectorName(v)  => fr"connectorname = $v" }
        ).flatten

        val whereClause: Fragment = conditions match {
          case Nil  => Fragment.empty
          case list => fr"WHERE" ++ list.reduceLeft(_ ++ fr" AND " ++ _)
        }

        val orderClause: Fragment = queryParams.collectFirst {
          case OBPOrdering(_, OBPAscending)  => fr"ORDER BY date ASC"
          case OBPOrdering(_, OBPDescending) => fr"ORDER BY date DESC"
        }.getOrElse(Fragment.empty)

        val limitClause: Fragment  = queryParams.collectFirst { case OBPLimit(v)  => fr"LIMIT $v"  }.getOrElse(Fragment.empty)
        val offsetClause: Fragment = queryParams.collectFirst { case OBPOffset(v) => fr"OFFSET $v" }.getOrElse(Fragment.empty)

        val select = fr"""SELECT connectorname, functionname, correlationid, date, duration,
                               requestparams, issuccessful, apiinstanceid
                          FROM mappedconnectormetric"""

        DoobieUtil.runQuery(
          (select ++ whereClause ++ orderClause ++ limitClause ++ offsetClause).query[MetricRow].to[List]
        ).map { row =>
          DoobieConnectorMetric(
            connectorName = row.connectorName,
            functionName  = row.functionName,
            correlationId = row.correlationId,
            date          = row.date.map(t => new Date(t.getTime)).getOrElse(new Date(0L)),
            duration      = row.duration,
            requestParams = row.requestParams,
            isSuccessful  = row.isSuccessful,
            apiInstanceId = row.apiInstanceId
          )
        }
      }
    }
  }

  override def bulkDeleteConnectorMetrics(): Boolean =
    DoobieUtil.runQuery(sql"DELETE FROM metric".update.run) >= 0

}
