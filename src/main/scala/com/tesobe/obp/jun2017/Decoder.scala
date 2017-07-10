package com.tesobe.obp.jun2017

import java.util.Date

import com.tesobe.obp.Util
import io.circe.generic.auto._
import io.circe.parser.decode


/**
  * Responsible for processing requests based on local example_import_jun2017.json file.
  *
  */
trait Decoder extends MappedDecoder {

  def getBanks(request: GetBanks) = {
    println("Enter getBanks")
    decodeLocalFile match {
      case Left(_) => Banks(request.authInfo, List.empty[InboundBank])
      case Right(x)  => Banks(AuthInfo("kurt","b9dfdd22-6e21-43b4-a0c7-fb3bd24f1298"), List(InboundBank("noerror","obp-bank-x-gh","The Bank of X","https://static.openbankproject.com/images/sandbox/bank_x.png","https://www.example.com")))
      //case Right(x) => Banks(request.authInfo, x.banks.map(mapBankN))
    }
  }

  def getBank(request: GetBank) = {
    decodeLocalFile match {
      case Left(_) => BankWrapper(request.authInfo, None)
      case Right(x) =>
        x.banks.filter(_.id == Some(request.bankId)).headOption match {
          case Some(x) => BankWrapper(request.authInfo, Some(mapBankN(x)))
          case None => BankWrapper(request.authInfo, None)
        }
    }
  }

  def getUser(request: GetUserByUsernamePassword) = {
    decodeLocalFile match {
      case Left(_) => UserWrapper(None)
      case Right(x) =>
        x.users.filter(_.displayName == Some(request.username)).filter(_.password == Some(request.password)).headOption match {
          case Some(x) => UserWrapper(Some(mapUserN(x)))
          case None => UserWrapper(None)
        }
    }
  }

  def getBankAccounts(request: GetUserBankAccounts) = {
    decodeLocalFile match {
      case Left(_) => BankAccounts(request.authInfo, Seq.empty[InboundAccount])
      case Right(x) => BankAccounts(request.authInfo, x.accounts.filter(_.bank == Some(request.bankId)).map(mapBankAccountN))
    }

  }
  
  def getAccounts(request: GetAccounts) = {
      println("Enter getAccounts")
       AccountsWrapper(request.authInfo,List(InboundAccountJune2017("hitest","hitest","hitest","hitest","hitest","hitest",
      "hitest","hitest",List("hitest"),List("hitest"),"hitest","hitest","hitest","hitest","hitest","hitest")))
  }

  def getAdapter(request: GetAdapterInfo) = {
    AdapterInfo(data = Some(InboundAdapterInfo("", "OBP-Scala-South", "June2017", Util.gitCommit, (new Date()).toString)))
  }
  
  def getUserByUsernamePassword(request: GetUserByUsernamePassword) = {
    InboundUser(Some("errorgubup"), Some("anil.x.0.gh@example.com"),Some("Anil_X.0.GH"))
  }


  /*
   * Decodes example_import_jun2017.json file
   */
  private val decodeLocalFile = {
    val resource = scala.io.Source.fromResource("example_import_jun2017.json")
    val lines = resource.getLines()
    val json = lines.mkString
    decode[com.tesobe.obp.jun2017.Example](json)
  }

}


object Decoder extends Decoder
