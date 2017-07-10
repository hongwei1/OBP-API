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
              case Some(x) => Map("data" -> mapBankN(x)).asJson.noSpaces
              case None => Map("data" -> InboundBank(BankNotFound, "", "", "", "")).asJson.noSpaces
            }
          case Some("obp.get.Banks") =>
            val data = example.banks.map(mapBankN)
            Map("data" -> data).asJson.noSpaces

          case Some("obp.get.User") =>
            example.users.filter(_.displayName == request.username).filter(_.password == request.password).headOption match {
              case Some(x) => Map("data" -> mapUserN(x)).asJson.noSpaces
              case None => Map("data" -> InboundValidatedUser(Some(BankNotFound), None, None)).asJson.noSpaces
            }
          case _ =>
            println
            Map("data" -> "Error, unrecognised request").asJson.noSpaces
        }
    }
  }

  def mapBankAccountN(x: Account) = {
    InboundAccount("",
      x.id.getOrElse(""),
      x.bank.getOrElse(""),
      x.label.getOrElse(""),
      x.number.getOrElse(""),
      x.`type`.getOrElse(""),
      x.balance.amount.getOrElse(""),
      x.balance.currency.getOrElse(""),
      x.IBAN.getOrElse(""),
      x.owners,
      x.generatePublicView,
      x.generateAccountantsView,
      x.generateAuditorsView,
      x.accountRoutingAddress.getOrElse(""),
      x.accountRoutingAddress.getOrElse(""),
      x.branchId.getOrElse(""))
  }

  def mapBankN(x: Bank) = {
    InboundBank("", x.id.getOrElse(""), x.fullName.getOrElse(""), x.logo.getOrElse(""), x.website.getOrElse(""))
  }

  def mapUserN(x: User) = {
    InboundValidatedUser(None, x.email, x.displayName)
  }

  private def extractQuery(request: Request): Option[String] = {
    request.action
  }

}
