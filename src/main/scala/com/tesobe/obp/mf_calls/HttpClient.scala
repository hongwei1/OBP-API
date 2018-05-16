package com.tesobe.obp
import java.io.FileInputStream
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.TimeUnit

import com.google.common.cache.CacheBuilder
import com.tesobe.CacheKeyFromArguments
import com.tesobe.obp.ErrorMessages.{InvalidRequestFormatException, _}
import com.tesobe.obp.JoniMf.config
import com.tesobe.obp.cache.Caching
import com.typesafe.scalalogging.StrictLogging
import net.liftweb.json.JsonAST.{JValue, compactRender}
import org.apache.http.HttpStatus
import org.apache.http.client.methods.{HttpGet, HttpPost}
import org.apache.http.conn.ssl.{SSLConnectionSocketFactory, TrustSelfSignedStrategy}
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClients
import org.apache.http.ssl.SSLContexts
import scalacache.guava.GuavaCache
import scalacache.{Flags, ScalaCache}


object HttpClient extends StrictLogging{

  val clientToCbs = if (config.getBoolean("ssl.use.ssl.cbs")) {
    
    val keyStorePassword = com.tesobe.obp.Main.clientCertificatePw
    logger.debug("keystore password is: " + keyStorePassword)
    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType)
    val inputStream = new FileInputStream(config.getString("ssl.keystore"))
    keyStore.load(inputStream, keyStorePassword.toArray)
    inputStream.close()
    
    val trustStore = KeyStore.getInstance(KeyStore.getDefaultType)
    val trustInputStream = new FileInputStream(config.getString("ssl.truststore"))
    trustStore.load(trustInputStream, keyStorePassword.toArray)
    trustInputStream.close()
    
    logger.debug("initialized keystore")


    val sslcontext = SSLContexts.custom().loadTrustMaterial(trustStore,
      new TrustSelfSignedStrategy()).loadKeyMaterial(keyStore, keyStorePassword.toCharArray()).build()
    
    logger.debug("set sslcontext")
    val sslsf = new SSLConnectionSocketFactory(sslcontext)
    logger.debug("set sslconnectionsocket")
    HttpClients.custom().setSSLSocketFactory(sslsf).build()
  } else {
    HttpClients.createDefault()
  }
  def makePostRequest(json: JValue, url: String): String = {
    val post = new HttpPost(url)
    post.addHeader("Content-Type", "application/json;charset=utf-8")
    val jsonBody = new StringEntity(compactRender(json), "UTF-8")
    post.setEntity(jsonBody)

    logger.debug(s"$url--Request : "+post.toString +"\n Body is :" + compactRender(json) +
    "/n RealBody is: " + jsonBody.getContent().toString)
    val response = clientToCbs.execute(post)
    val inputStream = response.getEntity.getContent
    val result = scala.io.Source.fromInputStream(inputStream).mkString
    response.close()
    logger.debug(s"$url--Response : "+response.toString+ "\n Body is :"+result)
    if (result.startsWith("<")) throw new InvalidRequestFormatException(s"$InvalidRequestFormat, current Request is $result") else result
  }
  def getLeumiBranchesFromBank: String = {
    var cacheKey = (UUID.randomUUID().toString, UUID.randomUUID().toString, UUID.randomUUID().toString)
    implicit val flags = Flags(readsEnabled = true)
    CacheKeyFromArguments.buildCacheKey{
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString())){
        getLeumiBranchesFromBankCached("1")
      }}}
  
  def getLeumiBranchesFromBankCached(forCache: String = "1"): String = {
    val client = HttpClients.createDefault()
    val url = config.getString("branches.url")
    logger.debug(s"Getting Leumi Branch Information from {$url}")
    val response = client.execute(new HttpGet(url))
    assert(response.getStatusLine.getStatusCode equals(HttpStatus.SC_OK))
    val inputStream = response.getEntity.getContent
    val result = scala.io.Source.fromInputStream(inputStream).mkString
    response.close()
    logger.debug("Response : "+response.toString+ "\n Body is :"+result)
    result
  }

  }

