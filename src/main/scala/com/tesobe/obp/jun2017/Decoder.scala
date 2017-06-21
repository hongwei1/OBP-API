package com.tesobe.obp.jun2017

import io.circe.generic.auto._
import io.circe.parser.decode


/**
  * Responsible for processing requests based on local example_import_jun2017.json file.
  *
  */
trait Decoder extends MappedDecoder {

  def getBanks(request: GetBanks) = {
    decodeLocalFile match {
      case Left(_) => Banks(request.authInfo, List.empty[InboundBank])
      case Right(x) => Banks(request.authInfo, x.banks.map(mapBankN))
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
