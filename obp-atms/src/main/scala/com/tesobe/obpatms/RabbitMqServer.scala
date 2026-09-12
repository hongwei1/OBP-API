package com.tesobe.obpatms

import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client._

import java.util
import java.util.concurrent.atomic.AtomicBoolean

/**
  * The consumer side of the exact protocol RabbitMQUtils.sendRequestUndGetResponseFromRabbitMQ
  * (obp-api) speaks from the other end: one shared request queue, AMQP messageId carries the
  * process name, reply goes to the AMQP replyTo queue with the AMQP correlationId echoed back.
  * Declares the request queue with the same durable/exclusive/autoDelete/args as the core does
  * (RabbitMQUtils.RPC_QUEUE_NAME / rpcQueueArgs) so whichever side starts first does not leave
  * the other unable to passively-declare it.
  */
class RabbitMqServer(config: RabbitMqConfig, handler: MessageHandler) {

  private val connectionFactory = new ConnectionFactory()
  connectionFactory.setHost(config.host)
  connectionFactory.setPort(config.port)
  connectionFactory.setUsername(config.username)
  connectionFactory.setPassword(config.password)
  connectionFactory.setVirtualHost(config.virtualHost)
  connectionFactory.setAutomaticRecoveryEnabled(true)
  connectionFactory.setNetworkRecoveryInterval(5000)

  @volatile private var connection: Connection = _
  @volatile private var channel: Channel = _
  private val running = new AtomicBoolean(false)

  def start(): Unit = {
    connection = connectionFactory.newConnection("obp-atms")
    channel = connection.createChannel()

    val requestQueueArgs = new util.HashMap[String, AnyRef]()
    requestQueueArgs.put("x-message-ttl", Integer.valueOf(60000))
    channel.queueDeclare(config.requestQueue, true, false, false, requestQueueArgs)
    // One in-flight message per consumer thread at a time: a slow DB write should not let the
    // broker pile up dozens of unacked deliveries on this channel.
    channel.basicQos(4)

    running.set(true)

    val deliverCallback: DeliverCallback = (_: String, delivery: Delivery) => {
      val props: BasicProperties = delivery.getProperties
      val messageId = props.getMessageId
      val correlationId = props.getCorrelationId
      val replyTo = props.getReplyTo
      val body = new String(delivery.getBody, "UTF-8")

      try {
        handler.handle(messageId, body) match {
          case Some(responseJson) =>
            if (replyTo != null) {
              val replyProps = new BasicProperties.Builder()
                .correlationId(correlationId)
                .contentType("application/json")
                .build()
              channel.basicPublish("", replyTo, replyProps, responseJson.getBytes("UTF-8"))
            }
          case None =>
            System.err.println(s"[obp-atms] ignoring message with unrecognised messageId=$messageId")
        }
      } catch {
        case t: Throwable =>
          System.err.println(s"[obp-atms] failed to handle messageId=$messageId: ${t.getMessage}")
      } finally {
        channel.basicAck(delivery.getEnvelope.getDeliveryTag, false)
      }
    }

    val cancelCallback: CancelCallback = (consumerTag: String) =>
      System.err.println(s"[obp-atms] consumer $consumerTag was cancelled")

    // autoAck = false: basicAck above only fires once the reply has actually been published (or
    // the failure has been logged), so a broker restart mid-request redelivers rather than loses.
    channel.basicConsume(config.requestQueue, false, deliverCallback, cancelCallback)
  }

  def isHealthy: Boolean = running.get() && connection != null && connection.isOpen && channel != null && channel.isOpen

  def stop(): Unit = {
    running.set(false)
    try if (channel != null && channel.isOpen) channel.close() catch { case _: Throwable => () }
    try if (connection != null && connection.isOpen) connection.close() catch { case _: Throwable => () }
  }
}
