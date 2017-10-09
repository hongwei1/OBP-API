package com.tesobe.obp

import akka.kafka.ConsumerMessage.CommittableMessage
import akka.stream.Materializer
import com.tesobe.obp.SouthKafkaStreamsActor.Business
import com.tesobe.obp.june2017._
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.{ExecutionContext, Future}
import io.circe.parser.decode
import io.circe.generic.auto._
import io.circe.syntax._
import net.liftweb.common.Failure

/**
  * Responsible for processing requests from North Side using local json files as data sources.
  *
  * Open Bank Project - Leumi Adapter
  * Copyright (C) 2016-2017, TESOBE Ltd.This program is free software: you can redistribute it and/or modify
  * it under the terms of the GNU Affero General Public License as published by
  * the Free Software Foundation, either version 3 of the License, or
  * (at your option) any later version.This program is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  * GNU Affero General Public License for more details.You should have received a copy of the GNU Affero General Public License
  * along with this program.  If not, see <http://www.gnu.org/licenses/>.Email: contact@tesobe.com
  * TESOBE Ltd
  * Osloerstrasse 16/17
  * Berlin 13359, GermanyThis product includes software developed at TESOBE (http://www.tesobe.com/)
  * This software may also be distributed under a commercial license from TESOBE Ltd subject to separate terms.
  */
class LocalProcessor(implicit executionContext: ExecutionContext, materializer: Materializer) extends StrictLogging with Config {

  /**
    * Processes message that comes from generic 'Request'/'Response' topics.
    * It has to resolve version from request first and based on that employ corresponding Decoder to extract response.
    * For convenience it is done in private method.
    *
    * @return Future of tuple2 containing message given from client and response given from corresponding Decoder.
    *         The form is defined in SouthKafkaStreamsActor
    */
  def generic: Business = { msg =>
    logger.info(s"Processing ${msg.record.value}")
    Future(msg, getResponse(msg))
  }

  /**
    * Processes message that comes from 'GetBanks' topic
    *
    * @return
    */
  def banksFn: Business = { msg =>
    /* call Decoder for extracting data from source file */
    logger.debug(s"Processing banksFn ${msg.record.value}")
    
    try {
      //This may throw exception:
      val response: (GetBanks => Banks) = {q => com.tesobe.obp.june2017.LeumiDecoder.getBanks(q)}
      //This also maybe throw exception, map the error to Exception 
      val r = decode[GetBanks](msg.record.value()) match {
        case Left(e) => throw new RuntimeException(s"Please check `$GetBanks` case class for OBP-API and Adapter sides : ", e)
        case Right(x) => response(x).asJson.noSpaces
      }
      Future(msg, r)
    } catch {
      case m: Throwable =>
        logger.error("banksFn-unknown error", m)
        val errorBody = Banks(
          AuthInfo("","",""),
          List(
            InboundBank(
              m.getMessage,
              List(
                InboundStatusMessage("ESB","Success", "0", "OK"), //TODO, need to fill the coreBanking error
                InboundStatusMessage("MF","Success", "0", "OK")  //TODO, need to fill the coreBanking error
            ),
             "", "","","")
          )
        )
        
        Future(msg, errorBody.asJson.noSpaces)
    }
   
  }

  def bankFn: Business = { msg =>
    logger.debug(s"Processing bankFn ${msg.record.value}")
    try {
      /* call Decoder for extracting data from source file */
      val response: (GetBank => BankWrapper) = { q => com.tesobe.obp.june2017.LeumiDecoder.getBank(q) }
      val r = decode[GetBank](msg.record.value()) match {
        case Left(e) => throw new RuntimeException(s"Please check `$GetBank` case class for OBP-API and Adapter sides : ", e);
        case Right(x) => response(x).asJson.noSpaces
      }
      Future(msg, r)
    } catch {
      case m: Throwable =>
        logger.error("banksFn-unknown error", m)
        val errorBody = BankWrapper(
          AuthInfo("","",""),
            InboundBank(
              m.getMessage,
              List(
                InboundStatusMessage("ESB","Success", "0", "OK"), //TODO, need to fill the coreBanking error
                InboundStatusMessage("MF","Success", "0", "OK")  //TODO, need to fill the coreBanking error
              ),
              "", "","","")
        )
      
        Future(msg, errorBody.asJson.noSpaces)
    }
  }
  
  def userFn: Business = { msg =>
    logger.debug(s"Processing userFn ${msg.record.value}")
    try {
      /* call Decoder for extracting data from source file */
      val response: (GetUserByUsernamePassword => UserWrapper) = { q => com.tesobe.obp.june2017.LeumiDecoder.getUser(q) }
      val r = decode[GetUserByUsernamePassword](msg.record.value()) match {
        case Left(e) => throw new RuntimeException(s"Please check `$GetUserByUsernamePassword` case class for OBP-API and Adapter sides : ", e);
        case Right(x) => response(x).asJson.noSpaces
      }
      Future(msg, r)
    } catch {
      case m: Throwable =>
        logger.error("banksFn-unknown error", m)
        val errorBody = UserWrapper(
          AuthInfo("","",""),
          InboundValidatedUser(
            m.getMessage,
            List(
              InboundStatusMessage("ESB","Success", "0", "OK"), //TODO, need to fill the coreBanking error
              InboundStatusMessage("MF","Success", "0", "OK")  //TODO, need to fill the coreBanking error
            ),
            "", "")
        )
        Future(msg, errorBody.asJson.noSpaces)
    }
  }
  
  def adapterFn: Business = { msg =>
    logger.debug(s"Processing adapterFn ${msg.record.value}")
    try {
    /* call Decoder for extracting data from source file */
      val response: (GetAdapterInfo => AdapterInfo) = { q => com.tesobe.obp.june2017.LeumiDecoder.getAdapter(q) }
      val r = decode[GetAdapterInfo](msg.record.value()) match {
        case Left(e) => throw new RuntimeException(s"Please check `$GetAdapterInfo` case class for OBP-API and Adapter sides : ", e);
        case Right(x) => response(x).asJson.noSpaces
      }
      Future(msg, r)
    } catch {
      case m: Throwable =>
        logger.error("banksFn-unknown error", m)
        val errorBody = AdapterInfo(
          InboundAdapterInfo(
            m.getMessage,
            List(
              InboundStatusMessage("ESB","Success", "0", "OK"), //TODO, need to fill the coreBanking error
              InboundStatusMessage("MF","Success", "0", "OK")  //TODO, need to fill the coreBanking error
            ),
            "", "","", "")
        )
        Future(msg, errorBody.asJson.noSpaces)
    }
  }
  
  def bankAccountIdFn: Business = { msg =>
    try {
      logger.debug(s"Processing bankAccountIdFn ${msg.record.value}")
      /* call Decoder for extracting data from source file */
      val response: (GetAccountbyAccountID => InboundBankAccount) = { q => com.tesobe.obp.june2017.LeumiDecoder.getBankAccountbyAccountId(q) }
      val r = decode[GetAccountbyAccountID](msg.record.value()) match {
        case Left(e) => throw new RuntimeException(s"Please check `$GetAccountbyAccountID` case class for OBP-API and Adapter sides : ", e);
        case Right(x) => response(x).asJson.noSpaces
      }
      Future(msg, r)
    } catch {
      case m: Throwable =>
        logger.error("banksFn-unknown error", m)
        val errorBody = InboundBankAccount(
            AuthInfo("","",""),
            InboundAccountJune2017(
              m.getMessage,
              List(
                InboundStatusMessage("ESB","Success", "0", "OK"), //TODO, need to fill the coreBanking error
                InboundStatusMessage("MF","Success", "0", "OK")   //TODO, need to fill the coreBanking error
              ),
              "", "","", "","", "","","",List(""),List(""),"", "","", "","","")
        )
        Future(msg, errorBody.asJson.noSpaces)
    }
  }

  def bankAccountNumberFn: Business = { msg =>
    logger.debug(s"Processing bankAccountNumberFn ${msg.record.value}")
    try {
      /* call Decoder for extracting data from source file */
      val response: (GetAccountbyAccountNumber => InboundBankAccount) = { q => com.tesobe.obp.june2017.LeumiDecoder.getBankAccountByAccountNumber(q) }
      val r = decode[GetAccountbyAccountNumber](msg.record.value()) match {
        case Left(e) => throw new RuntimeException(s"Please check `$GetAccountbyAccountNumber` case class for OBP-API and Adapter sides : ", e);
        case Right(x) => response(x).asJson.noSpaces
      }
      Future(msg, r)
    } catch {
      case m: Throwable =>
        logger.error("banksFn-unknown error", m)
        val errorBody = InboundBankAccount(
          AuthInfo("","",""),
          InboundAccountJune2017(
            m.getMessage,
            List(
              InboundStatusMessage("ESB","Success", "0", "OK"), //TODO, need to fill the coreBanking error
              InboundStatusMessage("MF","Success", "0", "OK")   //TODO, need to fill the coreBanking error
            ),
            "", "","", "","", "","","",List(""),List(""),"", "","", "","","")
        )
        Future(msg, errorBody.asJson.noSpaces)
    }
  }
  
  def bankAccountsFn: Business = {msg =>
    logger.debug(s"Processing bankAccountsFn ${msg.record.value}")
    try {
//    /* call Decoder for extracting data from source file */
      val response: (OutboundGetAccounts => InboundBankAccounts) = { q => com.tesobe.obp.june2017.LeumiDecoder.getBankAccounts(q) }
      val r = decode[OutboundGetAccounts](msg.record.value()) match {
        case Left(e) => throw new RuntimeException(s"Please check `$OutboundGetAccounts` case class for OBP-API and Adapter sides : ", e);
        case Right(x) => response(x).asJson.noSpaces
      }
      Future(msg, r)
    } catch {
      case m: Throwable =>
        logger.error("banksFn-unknown error", m)
        val errorBody = InboundBankAccounts(
          AuthInfo("","",""),
          List(InboundAccountJune2017(
            m.getMessage,
            List(
              InboundStatusMessage("ESB","Success", "0", "OK"), //TODO, need to fill the coreBanking error
              InboundStatusMessage("MF","Success", "0", "OK")   //TODO, need to fill the coreBanking error
            ),
            "", "","", "","", "","","",List(""),List(""),"", "","", "","","")
        ))
        Future(msg, errorBody.asJson.noSpaces)
    }
  }

  def transactionsFn: Business = {msg =>
    logger.debug(s"Processing transactionsFn ${msg.record.value}")
    try {
      /* call Decoder for extracting data from source file */
      val response: (GetTransactions => InboundTransactions) = { q => com.tesobe.obp.june2017.LeumiDecoder.getTransactions(q) }
      val r = decode[GetTransactions](msg.record.value()) match {
        case Left(e) => throw new RuntimeException(s"Please check `$GetTransactions` case class for OBP-API and Adapter sides : ", e);
        case Right(x) => response(x).asJson.noSpaces
      }
      Future(msg, r)
    } catch {
      case m: Throwable =>
        logger.error("banksFn-unknown error", m)
        val errorBody = InboundTransactions(
          AuthInfo("","",""),
          List(InternalTransaction(
            m.getMessage,
            List(
              InboundStatusMessage("ESB","Success", "0", "OK"), //TODO, need to fill the coreBanking error
              InboundStatusMessage("MF","Success", "0", "OK")   //TODO, need to fill the coreBanking error
            ),
            "", "","", "","", "","","","","","", "","", "")
          ))
        Future(msg, errorBody.asJson.noSpaces)
    }
  } 
  
  def transactionFn: Business = {msg =>
    logger.debug(s"Processing transactionFn ${msg.record.value}")
    try {
      /* call Decoder for extracting data from source file */
      val response: (GetTransaction => InboundTransaction) = { q => com.tesobe.obp.june2017.LeumiDecoder.getTransaction(q) }
      val r = decode[GetTransaction](msg.record.value()) match {
        case Left(e) => throw new RuntimeException(s"Please check `$GetTransaction` case class for OBP-API and Adapter sides : ", e);
        case Right(x) => response(x).asJson.noSpaces
      }
      Future(msg, r)
    } catch {
      case m: Throwable =>
        logger.error("banksFn-unknown error", m)
        
        val errorBody = InboundTransaction(
          AuthInfo("","",""),
          InternalTransaction(
            m.getMessage,
            List(
              InboundStatusMessage("ESB","Success", "0", "OK"), //TODO, need to fill the coreBanking error
              InboundStatusMessage("MF","Success", "0", "OK")   //TODO, need to fill the coreBanking error
            ),
            "", "","", "","", "","","","","","", "","", ""))
      
        Future(msg, errorBody.asJson.noSpaces)
    }
  }
  
  def createTransactionFn: Business = {msg =>
    logger.debug(s"Processing createTransactionFn ${msg.record.value}")
    try {
      /* call Decoder for extracting data from source file */
      val response: (CreateTransaction => InboundCreateTransactionId) = { q => com.tesobe.obp.june2017.LeumiDecoder.createTransaction(q) }
      val valueFromKafka: String = msg.record.value()
      //Because the CreateTransaction case class, contain the "sealed trait TransactionRequestCommonBodyJSON"
      //So, we need map the trait explicitly.
      def mapTraitFieldExplicitly(string: String) = valueFromKafka.replace(""""transactionRequestCommonBody":{""",s""""transactionRequestCommonBody":{"${string }": {""").replace("""}},""","""}}},""")
      
      val changeValue = 
        if(valueFromKafka.contains(Util.TransactionRequestTypes.TRANSFER_TO_PHONE.toString)) 
          mapTraitFieldExplicitly(TransactionRequestBodyTransferToPhoneJson.toString())
        else if(valueFromKafka.contains(Util.TransactionRequestTypes.COUNTERPARTY.toString))
          mapTraitFieldExplicitly(TransactionRequestBodyCounterpartyJSON.toString())
        else if(valueFromKafka.contains(Util.TransactionRequestTypes.SEPA.toString))
          mapTraitFieldExplicitly(TransactionRequestBodySEPAJSON.toString())
        else if(valueFromKafka.contains(Util.TransactionRequestTypes.TRANSFER_TO_ATM.toString))
          mapTraitFieldExplicitly(TransactionRequestBodyTransferToAtmJson.toString())
        else if(valueFromKafka.contains(Util.TransactionRequestTypes.TRANSFER_TO_ACCOUNT.toString))
          mapTraitFieldExplicitly(TransactionRequestBodyTransferToAccount.toString())
        else
          throw new RuntimeException("Do not support this transaction type, please check it in OBP-API side")
      
      val r = decode[CreateTransaction](changeValue) match {
        case Left(e) => throw new RuntimeException(s"Please check `$CreateTransaction` case class for OBP-API and Adapter sides : ",e);
        case Right(x) => response(x).asJson.noSpaces
      }
      Future(msg, r)
    } catch {
      case m: Throwable =>
        logger.error("banksFn-unknown error", m)
    
        val errorBody = InboundCreateTransactionId(
          AuthInfo("","",""),
          InternalTransactionId(
            m.getMessage,
            List(
              InboundStatusMessage("ESB","Success", "0", "OK"), //TODO, need to fill the coreBanking error
              InboundStatusMessage("MF","Success", "0", "OK")   //TODO, need to fill the coreBanking error
            ),
            ""))
    
        Future(msg, errorBody.asJson.noSpaces)
    }
  }
  
  def tokenFn: Business = {msg =>
    logger.debug(s"Processing tokenFn ${msg.record.value}")
    /* call Decoder for extracting data from source file */
    val response: (GetToken => InboundToken) = { q => com.tesobe.obp.june2017.LeumiDecoder.getToken(q) }
    val r = decode[GetToken](msg.record.value()) match {
      case Left(e) => throw new RuntimeException(s"Please check `$GetToken` case class for OBP-API and Adapter sides : ", e);
      case Right(x) => response(x).asJson.noSpaces
    }
    Future(msg, r)
  }
  
  def createChallengeFn: Business = {msg =>
    logger.debug(s"Processing createChallengeFn ${msg.record.value}")
    try {
      /* call Decoder for extracting data from source file */
      val response: (OutboundCreateChallengeJune2017 => InboundCreateChallengeJune2017) = { q => com.tesobe.obp.june2017.LeumiDecoder.createChallenge(q)}
      val r = decode[OutboundCreateChallengeJune2017](msg.record.value()) match {
        case Left(e) => throw new RuntimeException(s"Please check `$OutboundCreateChallengeJune2017` case class for OBP-API and Adapter sides : ", e);
        case Right(x) => response(x).asJson.noSpaces
      }
      Future(msg, r)
  } catch {
    case m: Throwable =>
      logger.error("banksFn-unknown error", m)
    
      val errorBody = InboundCreateChallengeJune2017(
        AuthInfo("","",""),
        InternalCreateChallengeJune2017(
          m.getMessage,
          List(
            InboundStatusMessage("ESB","Success", "0", "OK"), //TODO, need to fill the coreBanking error
            InboundStatusMessage("MF","Success", "0", "OK")   //TODO, need to fill the coreBanking error
          ),
          ""))
    
      Future(msg, errorBody.asJson.noSpaces)
  }
  }

  def getTransactionRequestsFn: Business = {msg =>
    logger.debug(s"Processing getTransactionRequestsFn ${msg.record.value}")
    try {
      /* call Decoder for extracting data from source file */
      val response: (GetTransactionRequests => InboundTransactions) = { q => com.tesobe.obp.june2017.LeumiDecoder.getTransactionRequests(q)}
      val r = decode[GetTransactionRequests](msg.record.value()) match {
        case Left(e) => throw new RuntimeException(s"Please check `$GetTransactionRequests` case class for OBP-API and Adapter sides : ", e);
        case Right(x) => response(x).asJson.noSpaces
      }
      Future(msg, r)
    } catch {
      case m: Throwable =>
        logger.error("getTransactionRequestsFn-unknown error", m)

        val errorBody = InboundCreateChallengeJune2017(
          AuthInfo("","",""),
          InternalCreateChallengeJune2017(
            m.getMessage,
            List(
              InboundStatusMessage("ESB","Success", "0", "OK"), //TODO, need to fill the coreBanking error
              InboundStatusMessage("MF","Success", "0", "OK")   //TODO, need to fill the coreBanking error
            ),
            ""))

        Future(msg, errorBody.asJson.noSpaces)
    }
  }
  
  private def getResponse(msg: CommittableMessage[String, String]): String = {
    decode[Request](msg.record.value()) match {
      case Left(e) => e.getLocalizedMessage
      case Right(r) =>
        val rr = r.version.isEmpty match {
          case true => r.copy(version = r.messageFormat)
          case false => r.copy(messageFormat = r.version)
        }
        rr.version match {
          case Some("Nov2016") => com.tesobe.obp.nov2016.Decoder.response(rr)
          case Some("Mar2017") => com.tesobe.obp.mar2017.Decoder.response(rr)
          case Some("June2017") => com.tesobe.obp.june2017.LeumiDecoder.response(rr)
          case _ => com.tesobe.obp.nov2016.Decoder.response(rr)
        }
    }
  }
}

object LocalProcessor {
  def apply()(implicit executionContext: ExecutionContext, materializer: Materializer): LocalProcessor =
    new LocalProcessor()
}

case class FileProcessingException(message: String) extends RuntimeException(message)
