package com.tesobe.obp

import java.time.LocalDate

import akka.kafka.ConsumerMessage.CommittableMessage
import akka.stream.Materializer
import com.tesobe.obp.SouthKafkaStreamsActor.Business
import com.tesobe.obp.june2017.LeumiDecoder.simpleTransactionDateFormat 
import com.tesobe.obp.Util.{MyLocalDateSerializer, MyZonedTimeDateSerializer}
import com.tesobe.obp.june2017._
import com.typesafe.scalalogging.StrictLogging
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import net.liftweb.json
import net.liftweb.json.Extraction
import net.liftweb.json.JsonAST.prettyRender

import scala.collection.immutable.List
import scala.concurrent.{ExecutionContext, Future}

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
  
  implicit val formats = net.liftweb.json.DefaultFormats + MyZonedTimeDateSerializer + MyLocalDateSerializer


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
      val response: (OutboundGetBanks => InboundGetBanks) = {q => com.tesobe.obp.june2017.LeumiDecoder.getBanks(q)}
      //This also maybe throw exception, map the error to Exception 
      val r = decode[OutboundGetBanks](msg.record.value()) match {
        case Left(e) => throw new RuntimeException(s"Please check `$OutboundGetBanks` case class for OBP-API and Adapter sides : ", e)
        case Right(x) => response(x).asJson.noSpaces
      }
      Future(msg, r)
    } catch {
      case m: Throwable =>
        logger.error("banksFn-unknown error", m)
        val errorBody = InboundGetBanks(
          AuthInfo("","",""),
          Status(errorCode = m.getMessage),
          List()
        )
        
        Future(msg, errorBody.asJson.noSpaces)
    }
   
  }

  def bankFn: Business = { msg =>
    logger.debug(s"Processing bankFn ${msg.record.value}")
    try {
      /* call Decoder for extracting data from source file */
      val response: (OutboundGetBank => InboundGetBank) = { q => com.tesobe.obp.june2017.LeumiDecoder.getBank(q) }
      val r = decode[OutboundGetBank](msg.record.value()) match {
        case Left(e) => throw new RuntimeException(s"Please check `$OutboundGetBank` case class for OBP-API and Adapter sides : ", e);
        case Right(x) => response(x).asJson.noSpaces
      }
      Future(msg, r)
    } catch {
      case m: Throwable =>
        logger.error("bankFn-unknown error", m)
        val errorBody = InboundGetBank(
          AuthInfo("","",""),
          Status(errorCode = m.getMessage),
          null
        )
      
        Future(msg, errorBody.asJson.noSpaces)
    }
  }
  
  def userFn: Business = { msg =>
    logger.debug(s"Processing userFn ${msg.record.value}")
    try {
      /* call Decoder for extracting data from source file */
      val response: (OutboundGetUserByUsernamePassword => InboundGetUserByUsernamePassword) = { q => com.tesobe.obp.june2017.LeumiDecoder.getUser(q) }
      val r = decode[OutboundGetUserByUsernamePassword](msg.record.value()) match {
        case Left(e) => throw new RuntimeException(s"Please check `$OutboundGetUserByUsernamePassword` case class for OBP-API and Adapter sides : ", e);
        case Right(x) => response(x).asJson.noSpaces
      }
      Future(msg, r)
    } catch {
      case m: Throwable =>
        logger.error("userFn-unknown error", m)
        val errorBody = InboundGetUserByUsernamePassword(
          AuthInfo("","",""),
          InboundValidatedUser(
            m.getMessage,
            List(
              InboundStatusMessage("ESB","Success", "0", "OK"),
              InboundStatusMessage("MF","Success", "0", "OK") 
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
      val response: (OutboundGetAdapterInfo => InboundAdapterInfo) = { q => com.tesobe.obp.june2017.LeumiDecoder.getAdapter(q) }
      val r = decode[OutboundGetAdapterInfo](msg.record.value()) match {
        case Left(e) => throw new RuntimeException(s"Please check `$OutboundGetAdapterInfo` case class for OBP-API and Adapter sides : ", e);
        case Right(x) => response(x).asJson.noSpaces
      }
      Future(msg, r)
    } catch {
      case m: Throwable =>
        logger.error("adapterFn-unknown error", m)
        val errorBody = InboundAdapterInfo(
          InboundAdapterInfoInternal(
            m.getMessage,
            List(
              InboundStatusMessage("ESB","Success", "0", "OK"),
              InboundStatusMessage("MF","Success", "0", "OK") 
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
      val response: (OutboundGetAccountbyAccountID => InboundGetAccountbyAccountID) = { q => com.tesobe.obp.june2017.LeumiDecoder.getBankAccountbyAccountId(q) }
      val r = decode[OutboundGetAccountbyAccountID](msg.record.value()) match {
        case Left(e) => throw new RuntimeException(s"Please check `$OutboundGetAccountbyAccountID` case class for OBP-API and Adapter sides : ", e);
        case Right(x) => response(x).asJson.noSpaces
      }
      Future(msg, r)
    } catch {
      case m: Throwable =>
        logger.error("bankAccountIdFn-unknown error", m)
        val errorBody = InboundGetAccountbyAccountID(
            AuthInfo("","",""),
            InboundAccountJune2017(
              m.getMessage,
              List(
                InboundStatusMessage("ESB","Success", "0", "OK"),
                InboundStatusMessage("MF","Success", "0", "OK")  
              ),
              "", "","", "","", "","","",List(""),List(""),"", "","", "","","",Nil)
        )
        Future(msg, errorBody.asJson.noSpaces)
    }
  }
  
  def checkBankAccountExistsFn: Business = { msg =>
    try {
      logger.debug(s"Processing checkBankAccountExistsFn ${msg.record.value}")
      /* call Decoder for extracting data from source file */
      val kafkaRecordValue = msg.record.value()
      val outboundCheckBankAccountExists  = Extraction.extract[OutboundCheckBankAccountExists](json.parse(kafkaRecordValue))
      val inboundGetAccountbyAccountID = com.tesobe.obp.june2017.LeumiDecoder.checkBankAccountExists(outboundCheckBankAccountExists)
      val r = prettyRender(Extraction.decompose(inboundGetAccountbyAccountID))
      
      Future(msg, r)
    } catch {
      case m: Throwable =>
        logger.error("checkBankAccountExistsFn-unknown error", m)
        val errorBody = InboundGetAccountbyAccountID(
          AuthInfo("","",""),
          InboundAccountJune2017(
            m.getMessage,
            List(
              InboundStatusMessage("ESB","Success", "0", "OK"),
              InboundStatusMessage("MF","Success", "0", "OK")  
            ),
            "", "","", "","", "","","",List(""),List(""),"", "","", "","","",Nil)
        )
        Future(msg, errorBody.asJson.noSpaces)
    }
  }

    
  def bankAccountsFn: Business = {msg =>
    logger.debug(s"Processing bankAccountsFn ${msg.record.value}")
    try {
//    /* call Decoder for extracting data from source file */
      val response: (OutboundGetAccounts => InboundGetAccounts) = { q => com.tesobe.obp.june2017.LeumiDecoder.getBankAccounts(q) }
      val r = decode[OutboundGetAccounts](msg.record.value()) match {
        case Left(e) => throw new RuntimeException(s"Please check `$OutboundGetAccounts` case class for OBP-API and Adapter sides : ", e);
        case Right(x) => response(x).asJson.noSpaces
      }
      Future(msg, r)
    } catch {
      case m: Throwable =>
        logger.error("bankAccountsFn-unknown error", m)
        val errorBody = InboundGetAccounts(
          AuthInfo("","",""),
          List(InboundAccountJune2017(
            m.getMessage,
            List(
              InboundStatusMessage("ESB","Success", "0", "OK"),
              InboundStatusMessage("MF","Success", "0", "OK")  
            ),
            "", "","", "","", "","","",List(""),List(""),"", "","", "","","",Nil)
        ))
        Future(msg, errorBody.asJson.noSpaces)
    }
  }


  def getCoreBankAccountsFn: Business = {msg =>
    logger.debug(s"Processing getCoreBankAccountsFn ${msg.record.value}")
    try {
      //    /* call Decoder for extracting data from source file */
      val response: (OutboundGetCoreBankAccounts => InboundGetCoreBankAccounts) = { q => com.tesobe.obp.june2017.LeumiDecoder.getCoreBankAccounts(q) }
      val r = decode[OutboundGetCoreBankAccounts](msg.record.value()) match {
        case Left(e) => throw new RuntimeException(s"Please check `$OutboundGetCoreAccounts` case class for OBP-API and Adapter sides : ", e);
        case Right(x) => response(x).asJson.noSpaces
      }
      Future(msg, r)
    } catch {
      case m: Throwable =>
        logger.error("getCoreBankAccountsFn-unknown error", m)
        val errorBody = InboundGetCoreBankAccounts(
          AuthInfo("","",""),List(InternalInboundCoreAccount(
            m.getMessage,
          List(
            InboundStatusMessage("ESB","Success", "0", "OK"),
            InboundStatusMessage("MF","Success", "0", "OK")  
          ),
          "","", "", AccountRouting("",""))))
        
        Future(msg, errorBody.asJson.noSpaces)
    }
  }

  def getCustomerFn: Business = {msg =>
    logger.debug(s"Processing getCustomerFn ${msg.record.value}")
    try {
      /* call Decoder for extracting data from source file */
      val kafkaRecordValue = msg.record.value()
      val outboundGetCustomerByUserId  = Extraction.extract[OutboundGetCustomersByUserId](json.parse(kafkaRecordValue))
      val inboundGetCustomersByUserId  = com.tesobe.obp.june2017.LeumiDecoder.getCustomer(outboundGetCustomerByUserId)
      val r = prettyRender(Extraction.decompose(inboundGetCustomersByUserId))
      Future(msg, r)
    } catch {
      case m: Throwable =>
        logger.error("getCustomerFn-unknown error", m)
        val errorBody = InboundGetCustomersByUserId(AuthInfo("","",""), Status(errorCode = m.getMessage),null)
        Future(msg, prettyRender(Extraction.decompose(errorBody)))
    }
  }


  def transactionsFn: Business = { msg =>
    try {
      logger.debug(s"Processing transactionsFn ${msg.record.value}")
      /* call Decoder for extracting data from source file */
      val kafkaRecordValue = msg.record.value()
      val outboundGetTransactions  = Extraction.extract[OutboundGetTransactions](json.parse(kafkaRecordValue))
      val inboundGetTransactions = com.tesobe.obp.june2017.LeumiDecoder.getTransactions(outboundGetTransactions)
      val r = prettyRender(Extraction.decompose(inboundGetTransactions))

      Future(msg, r)
    } catch {
      case m: Throwable =>
        logger.error("transactionsFn-unknown error", m)
        val errorBody = InboundGetTransactions(AuthInfo("","",""), Status(errorCode = m.getMessage), Nil)
        Future(msg, prettyRender(Extraction.decompose(errorBody)))
    }
  } 
  
  def transactionFn: Business = {msg =>
    logger.debug(s"Processing transactionFn ${msg.record.value}")
    try {
      logger.debug(s"Processing transactionsFn ${msg.record.value}")
      /* call Decoder for extracting data from source file */
      val kafkaRecordValue = msg.record.value()
      val outboundGetTransaction  = Extraction.extract[OutboundGetTransaction](json.parse(kafkaRecordValue))
      val inboundGetTransaction = com.tesobe.obp.june2017.LeumiDecoder.getTransaction(outboundGetTransaction)
      val r = prettyRender(Extraction.decompose(inboundGetTransaction))

      Future(msg, r)
    } catch {
      case m: Throwable =>
        logger.error("transactionFn-unknown error", m)
        
        val errorBody = InboundGetTransaction(
          AuthInfo("","",""),
          Status(errorCode = m.getMessage),
          None)
      
        Future(msg, prettyRender(Extraction.decompose(errorBody)))
    }
  }
  
  def createTransactionFn: Business = {msg =>
    logger.debug(s"Processing createTransactionFn ${msg.record.value}")
    try {
      /* call Decoder for extracting data from source file */
      val response: (OutboundCreateTransaction => InboundCreateTransactionId) = { q => com.tesobe.obp.june2017.LeumiDecoder.createTransaction(q) }
      val valueFromKafka: String = msg.record.value()
      //Because the CreateTransaction case class, contain the "sealed trait TransactionRequestCommonBodyJSON"
      //So, we need map the trait explicitly.
      def mapTraitFieldExplicitly(transactionRequestType: String) = valueFromKafka.replace(""""transactionRequestCommonBody":{""",s""""transactionRequestCommonBody":{"${transactionRequestType }": {""").replace("""}},""","""}}},""")
      
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
      
      val r = decode[OutboundCreateTransaction](changeValue) match {
        case Left(e) => throw new RuntimeException(s"Please check `$OutboundCreateTransaction` case class for OBP-API and Adapter sides : ",e);
        case Right(x) => response(x).asJson.noSpaces
      }
      Future(msg, r)
    } catch {
      case m: Throwable =>
        logger.error("createTransactionFn-unknown error", m)
    
        val errorBody = InboundCreateTransactionId(
          AuthInfo("","",""),
          InternalTransactionId(
            m.getMessage,
            List(
              InboundStatusMessage("ESB","Success", "0", "OK"),
              InboundStatusMessage("MF","Success", "0", "OK")  
            ),
            ""))
    
        Future(msg, errorBody.asJson.noSpaces)
    }
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
      logger.error("createChallengeFn-unknown error", m)
    
      val errorBody = InboundCreateChallengeJune2017(
        AuthInfo("","",""),
        InternalCreateChallengeJune2017(
          m.getMessage,
          List(
            InboundStatusMessage("ESB","Success", "0", "OK"),
            InboundStatusMessage("MF","Success", "0", "OK")  
          ),
          ""))
    
      Future(msg, errorBody.asJson.noSpaces)
  }
  }

  def getTransactionRequestsFn: Business = {msg =>
    logger.debug(s"Processing getTransactionRequestsFn ${msg.record.value}")
    try {
      /* call Decoder for extracting data from source file */
      val kafkaRecordValue = msg.record.value()
      val outboundGetTransactionRequests210  = Extraction.extract[OutboundGetTransactionRequests210](json.parse(kafkaRecordValue))
      val inboundGetTransactionRequests210 = com.tesobe.obp.june2017.LeumiDecoder.getTransactionRequests(outboundGetTransactionRequests210)
      val r = prettyRender(Extraction.decompose(inboundGetTransactionRequests210))
      Future(msg, r)
    } catch {
      case m: Throwable =>
        logger.error("getTransactionRequestsFn-unknown error", m)

        val errorBody = InboundGetTransactionRequests210(AuthInfo("","",""), Status(errorCode = m.getMessage), null)
        Future(msg, prettyRender(Extraction.decompose(errorBody)))
    }
  }
  
  
  def createCounterpartyFn: Business = {msg =>
    logger.debug(s"Processing createCounterpartyFn ${msg.record.value}")
    try {
      /* call Decoder for extracting data from source file */
      val response: (OutboundCreateCounterparty => InboundCreateCounterparty) = { q => com.tesobe.obp.june2017.LeumiDecoder.createCounterparty(q)}
      val r = decode[OutboundCreateCounterparty](msg.record.value()) match {
        case Left(e) => throw new RuntimeException(s"Please check `$OutboundCreateCounterparty` case class for OBP-API and Adapter sides : ", e);
        case Right(x) => response(x).asJson.noSpaces
      }
      Future(msg, r)
    } catch {
      case m: Throwable =>
        logger.error("createCounterpartyFn-unknown error", m)
        
        val errorBody = InboundCreateChallengeJune2017(
          AuthInfo("","",""),
          InternalCreateChallengeJune2017(
            m.getMessage,
            List(
              InboundStatusMessage("ESB","Success", "0", "OK"),
              InboundStatusMessage("MF","Success", "0", "OK")  
            ),
            false.toString
          )
        )
        
        Future(msg, errorBody.asJson.noSpaces)
    }
  }
  def getCounterpartiesFn: Business = {msg =>
    logger.debug(s"Processing getCounterpartiesFn ${msg.record.value}")
    try {
      /* call Decoder for extracting data from source file */
      val response: (OutboundGetCounterparties => InboundGetCounterparties) = { q => com.tesobe.obp.june2017.LeumiDecoder.getCounterpartiesForAccount(q)}
      val r = decode[OutboundGetCounterparties](msg.record.value()) match {
        case Left(e) => throw new RuntimeException(s"Please check `$OutboundCreateCounterparty` case class for OBP-API and Adapter sides : ", e);
        case Right(x) => response(x).asJson.noSpaces
      }
      Future(msg, r)
    } catch {
      case m: Throwable =>
        logger.error("getCounterpartiesFn-unknown error", m)   

        val errorBody = InboundGetCounterparties(AuthInfo("","",""),Status(m.getLocalizedMessage) , List(InternalCounterparty(createdByUserId = "", name = "", thisBankId = "", thisAccountId = "", thisViewId = "", counterpartyId = "", otherAccountRoutingScheme= "", otherAccountRoutingAddress= "", otherBankRoutingScheme= "", otherBankRoutingAddress= "", otherBranchRoutingScheme= "", otherBranchRoutingAddress= "", isBeneficiary = false, description = "", otherAccountSecondaryRoutingScheme= "", otherAccountSecondaryRoutingAddress= "", bespoke = List(PostCounterpartyBespoke("englishName", ""),
                            PostCounterpartyBespoke("englishDescription", "")


                          ))))

        Future(msg, errorBody.asJson.noSpaces)
    }
  }

  def getCounterpartyByIdFn: Business = {msg =>
    logger.debug(s"Processing getCounterpartyByIdFn ${msg.record.value}")
    try {
      /* call Decoder for extracting data from source file */
      val response: (OutboundGetCounterpartyByCounterpartyId => InboundGetCounterparty) = { q => com.tesobe.obp.june2017.LeumiDecoder.getCounterpartyByCounterpartyId(q)}
      val r = decode[OutboundGetCounterpartyByCounterpartyId](msg.record.value()) match {
        case Left(e) => throw new RuntimeException(s"Please check `$OutboundGetCounterpartyByCounterpartyId` case class for OBP-API and Adapter sides : ", e);
        case Right(x) => response(x).asJson.noSpaces
      }
      Future(msg, r)
    } catch {
      case m: Throwable =>
        logger.error("getCounterpartiesFn-unknown error", m)

        val errorBody = InboundGetCounterparty(AuthInfo("","",""), Status(errorCode = m.getMessage), None)
        Future(msg, errorBody.asJson.noSpaces)
    }
  }

  def getCounterpartyFn: Business = {msg =>
    logger.debug(s"Processing getCounterpartyFn ${msg.record.value}")
    try {
      /* call Decoder for extracting data from source file */
      val response: (OutboundGetCounterparty => InboundGetCounterparty) = { q => com.tesobe.obp.june2017.LeumiDecoder.getCounterparty(q)}
      val r = decode[OutboundGetCounterparty](msg.record.value()) match {
        case Left(e) => throw new RuntimeException(s"Please check `$OutboundGetCounterparty` case class for OBP-API and Adapter sides : ", e);
        case Right(x) => response(x).asJson.noSpaces
      }
      Future(msg, r)
    } catch {
      case m: Throwable =>
        logger.error("getCounterpartyFn-unknown error", m)

        val errorBody = InboundGetCounterparty(AuthInfo("","",""), Status(errorCode = m.getMessage), None)
        Future(msg, errorBody.asJson.noSpaces)
    }
  }

  def getBranchesFn: Business = { msg =>
    try {
      logger.debug(s"Processing getBranchesFn ${msg.record.value}")
      /* call Decoder for extracting data from source file */
      val kafkaRecordValue = msg.record.value()
      val outboundGetBranches  = Extraction.extract[OutboundGetBranches](json.parse(kafkaRecordValue))
      val inboundGetBranches = com.tesobe.obp.june2017.LeumiDecoder.getBranches(outboundGetBranches)
      val r = prettyRender(Extraction.decompose(inboundGetBranches))

      Future(msg, r)
    } catch {
      case m: Throwable =>
        logger.error("checkBankAccountExistsFn-unknown error", m)
        val errorBody = InboundGetBranches(
          AuthInfo("","",""),
          Status(errorCode = m.getMessage),
          List()
        )
        Future(msg, errorBody.asJson.noSpaces)
    }
  }

  def getBranchFn: Business = { msg =>
    try {
      logger.debug(s"Processing getBranchFn ${msg.record.value}")
      /* call Decoder for extracting data from source file */
      val kafkaRecordValue = msg.record.value()
      val outboundGetBranch  = Extraction.extract[OutboundGetBranch](json.parse(kafkaRecordValue))
      val inboundGetBranch = com.tesobe.obp.june2017.LeumiDecoder.getBranch(outboundGetBranch)
      val r = prettyRender(Extraction.decompose(inboundGetBranch))

      Future(msg, r)
    } catch {
      case m: Throwable =>
        logger.error("getBranchFn-unknown error", m)
        val errorBody = InboundGetBranch(
          AuthInfo("","",""),
          Status(errorCode = m.getMessage),None
        )
        Future(msg, errorBody.asJson.noSpaces)
    }
  }

  def getAtmsFn: Business = { msg =>
    try {
      logger.debug(s"Processing getAtmsFn ${msg.record.value}")
      /* call Decoder for extracting data from source file */
      val kafkaRecordValue = msg.record.value()
      val outboundGetAtms  = Extraction.extract[OutboundGetAtms](json.parse(kafkaRecordValue))
      val inboundGetAtms = com.tesobe.obp.june2017.LeumiDecoder.getAtms(outboundGetAtms)
      val r = prettyRender(Extraction.decompose(inboundGetAtms))

      Future(msg, r)
    } catch {
      case m: Throwable =>
        logger.error("getAtms-unknown error", m)
        val errorBody = InboundGetAtms(
          AuthInfo("","",""),
          Status(errorCode = m.getMessage),List()

        )
        Future(msg, errorBody.asJson.noSpaces)
    }
  }

  def getAtmFn: Business = { msg =>
    try {
      logger.debug(s"Processing getAtmFn ${msg.record.value}")
      /* call Decoder for extracting data from source file */
      val kafkaRecordValue = msg.record.value()
      val outboundGetAtm  = Extraction.extract[OutboundGetAtm](json.parse(kafkaRecordValue))
      val inboundGetAtm = com.tesobe.obp.june2017.LeumiDecoder.getAtm(outboundGetAtm)
      val r = prettyRender(Extraction.decompose(inboundGetAtm))

      Future(msg, r)
    } catch {
      case m: Throwable =>
        logger.error("getAtm-unknown error", m)
        val errorBody = InboundGetAtm(
          AuthInfo("","",""),
          Status(errorCode = m.getMessage),
          None
        )
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
