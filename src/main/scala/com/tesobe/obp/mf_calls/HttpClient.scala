package com.tesobe.obp
import java.util.concurrent.TimeUnit

import com.google.common.cache.CacheBuilder
import com.tesobe.obp.ErrorMessages.InvalidRequestFormatException
import com.tesobe.obp.JoniMf.config
import com.typesafe.scalalogging.StrictLogging
import net.liftweb.json.JsonAST.{JValue, compactRender}
import org.apache.http.client.methods.{HttpGet, HttpPost}
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClients
import com.tesobe.obp.ErrorMessages._
import com.tesobe.obp.Ntlv1Mf.underlyingGuavaCache
import org.apache.http.HttpStatus

import scalacache.ScalaCache
import scalacache.guava.GuavaCache


object HttpClient extends StrictLogging{

  val underlyingGuavaCache = CacheBuilder.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).build[String, Object]
  implicit val scalaCache  = ScalaCache(GuavaCache(underlyingGuavaCache))
  import scalacache.memoization.memoizeSync


  def makePostRequest(json: JValue, path: String): String = {
    
    val client = HttpClients.createDefault()
    val url = config.getString("bankserver.url")
    val post = new HttpPost(url + path)
    post.addHeader("Content-Type", "application/json;charset=utf-8")
    val jsonBody = new StringEntity(compactRender(json), "UTF-8")
    post.setEntity(jsonBody)

    logger.debug(s"$path--Request : "+post.toString +"\n Body is :" + compactRender(json) +
    "/n RealBody is: " + jsonBody.getContent().toString)
    val response = client.execute(post)
    val inputStream = response.getEntity.getContent
    val result = scala.io.Source.fromInputStream(inputStream).mkString
    response.close()
    logger.debug(s"$path--Response : "+response.toString+ "\n Body is :"+result)
    if (result.startsWith("<")) throw new InvalidRequestFormatException(s"$InvalidRequestFormat, current Request is $result") else result
  }
  def getLeumiBranchesFromBank: String = memoizeSync {
    getLeumiBranchesFromBankCached("1")
  }
  def getLeumiBranchesFromBankCached(forCache: String = "1"): String = {
    val client = HttpClients.createDefault()
    //TODO: will go to the config file
    val url = "http://www.bankleumi.co.il/vgnprod/xml/branchesExpanded.xml"
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


