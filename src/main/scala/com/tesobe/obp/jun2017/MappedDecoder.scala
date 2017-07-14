package com.tesobe.obp.jun2017

import com.tesobe.obp.Request
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._

/**
  * Support for old style messaging.
  *
  */
trait MappedDecoder {

  val BankNotFound = "OBP-30001: Bank not found. Please specify a valid value for BANK_ID."

  def response(request: Request): String = {
    val resource = scala.io.Source.fromResource("example_import_jun2017.json")
    val lines = resource.getLines()
    val json = lines.mkString
    val d = decode[com.tesobe.obp.jun2017.Example](json)
    d match {
      case Left(err) => Map("data" -> err.getMessage).asJson.noSpaces
      case Right(example) =>
        extractQuery(request) match {
          case Some("obp.get.Bank") =>
            example.banks.filter(_.id == Some(request.bankId)).headOption match {
              case Some(x) => Map("data" -> mapBankToInboundBank(x)).asJson.noSpaces
              case None => Map("data" -> InboundBank(BankNotFound, "", "", "", "")).asJson.noSpaces
            }
          case Some("obp.get.Banks") =>
            val data = example.banks.map(mapBankToInboundBank)
            Map("data" -> data).asJson.noSpaces

          case Some("obp.get.User") =>
            example.users.filter(_.displayName == request.username).filter(_.password == request.password).headOption match {
              case Some(x) => Map("data" -> mapUserToInboundValidatedUser(x)).asJson.noSpaces
              case None => Map("data" -> InboundValidatedUser(Some(BankNotFound), None, None)).asJson.noSpaces
            }
          case _ =>
            Map("data" -> "Error, unrecognised request").asJson.noSpaces
      }
    }
  }

  def mapBankToInboundBank(x: Bank) = {
    InboundBank("", x.id.getOrElse(""), x.fullName.getOrElse(""), x.logo.getOrElse(""), x.website.getOrElse(""))
  }

  def mapUserToInboundValidatedUser(x: User) = {
    InboundValidatedUser(None, x.email, x.displayName)
  }
  
  def mapAdapterAccountToInboundAccountJune2017(x: Account) = {
    InboundAccountJune2017(
      errorCode = "",
      bankId = x.bank.get,
      branchId = x.branchId.get,
      accountId = x.branchId.get,
      accountNr = x.branchId.get,
      accountType = x.branchId.get,
      balanceAmount = x.branchId.get,
      balanceCurrency = x.branchId.get,
      owners = x.owners,
      viewsToGenerate = x.owners,
      bankRoutingScheme = x.bank.get,
      bankRoutingAddress = x.bank.get,
      branchRoutingScheme = x.bank.get,
      branchRoutingAddress = x.bank.get,
      accountRoutingScheme = x.bank.get,
      accountRoutingAddress = x.bank.get
    )
  }

  private def extractQuery(request: Request): Option[String] = {
    request.action
  }

}
