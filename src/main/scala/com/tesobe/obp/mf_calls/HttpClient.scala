package com.tesobe.obp
import com.tesobe.obp.ErrorMessages.InvalidRequestFormatException
import com.tesobe.obp.JoniMf.config
import com.typesafe.scalalogging.StrictLogging
import net.liftweb.json.JsonAST.{JValue, compactRender}
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.DefaultHttpClient
import com.tesobe.obp.ErrorMessages._


object HttpClient extends StrictLogging{
  
  
  def makePostRequest(json: JValue, path: String): String = {
    
    val client = new DefaultHttpClient()
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

  }


