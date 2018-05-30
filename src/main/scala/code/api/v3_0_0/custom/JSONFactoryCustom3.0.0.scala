/**
Open Bank Project - API
Copyright (C) 2011-2016, TESOBE Ltd

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE Ltd
Osloerstrasse 16/17
Berlin 13359, Germany

  This product includes software developed at
  TESOBE (http://www.tesobe.com/)

 */
package code.api.v3_0_0.custom

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.amountOfMoneyJsonV121
import code.api.util.APIUtil
import code.api.v1_2_1.AmountOfMoneyJsonV121
import code.api.v2_1_0.TransactionRequestCommonBodyJSON

//for create transaction request
case class ToAccountTransferToPhoneJson(
  mobile_phone_number: String
)

case class FromAccountTransferJson (
  mobile_phone_number: String,
  nickname: String
)

case class TransactionRequestBodyTransferToPhoneJson(
  value: AmountOfMoneyJsonV121,
  description: String,
  message: String,
  from: FromAccountTransferJson,
  to: ToAccountTransferToPhoneJson
) extends TransactionRequestCommonBodyJSON

case class ToAccountTransferToAtmKycDocumentJson(
  `type`: String,
  number: String
)

case class ToAccountTransferToAtmJson(
  legal_name: String,
  date_of_birth: String,
  mobile_phone_number: String,
  kyc_document: ToAccountTransferToAtmKycDocumentJson
)

case class TransactionRequestBodyTransferToAtmJson(
  value: AmountOfMoneyJsonV121,
  description: String,
  message: String,
  from: FromAccountTransferJson,
  to: ToAccountTransferToAtmJson
) extends TransactionRequestCommonBodyJSON

case class ToAccountTransferToAccountAccountJson(
  number: String,
  iban: String
)

case class ToAccountTransferToAccountJson(
  name: String,
  bank_code: String,
  branch_number : String,
  account:ToAccountTransferToAccountAccountJson
)

case class TransactionRequestBodyTransferToAccount(
  value: AmountOfMoneyJsonV121,
  description: String,
  transfer_type: String,
  future_date: String,
  to: ToAccountTransferToAccountJson
) extends TransactionRequestCommonBodyJSON

object JSONFactoryCustom300{
  val toAccountTransferToPhoneJson = ToAccountTransferToPhoneJson("+9722398746")
  val fromAccountTransferToPhoneJson = FromAccountTransferJson(
    mobile_phone_number="Mobile number of the money sender (10 digits),eg: +9722398712",
    nickname="Tom"
  )
  val transactionRequestBodyTransferToPhoneJson = TransactionRequestBodyTransferToPhoneJson(
    value = amountOfMoneyJsonV121,
    description = "Transaction description/purpose (20 symbols)",
    message ="Message text to the money receiver (50 symbols)",
    from = fromAccountTransferToPhoneJson,
    to = toAccountTransferToPhoneJson
  )

  val toAccountTransferToAtmKycDocumentJson = ToAccountTransferToAtmKycDocumentJson(
    `type` = "ID Type of the money receiver: 1 - National; 5- Passport",
    number = " Passport or National ID number of the money receiver"
  )

  val toAccountTransferToAtmJson = ToAccountTransferToAtmJson(
    legal_name = "Thomas Andrew Smith",
    date_of_birth = "19900101",
    mobile_phone_number = "Mobile number of the money sender (10 digits),eg: +9722398746",
    kyc_document = toAccountTransferToAtmKycDocumentJson
  )

  val transactionRequestBodyTransferToAtmJson = TransactionRequestBodyTransferToAtmJson(
    value = amountOfMoneyJsonV121,
    description = "Transaction description/purpose (20 symbols)",
    message = "Message text to the money receiver (50 symbols)",
    from = fromAccountTransferToPhoneJson,
    to = toAccountTransferToAtmJson
  )

  val toAccountTransferToAccountAccountJson = ToAccountTransferToAccountAccountJson(
    number = "Account number of the target account",
    iban ="IBAN of the target account for RTGS transfer - if presented then bank/branch/account details are ignored"
  )

  val toAccountTransferToAccountJson= ToAccountTransferToAccountJson(
    name = "Tom Muller - has to be english if transfer_type = RealTime",
    bank_code = "Bank code of the target account",
    branch_number = "Branch number of the target account",
    account = toAccountTransferToAccountAccountJson
  )

  val transactionRequestBodyAccountToAccount = TransactionRequestBodyTransferToAccount(
    value = amountOfMoneyJsonV121,
    description = "Transaction description/purpose (20 symbols)",
    transfer_type = "Transfer type: regular=regular; RealTime=RTGS - real time",
    future_date = "The future date (see K050_SW_ATIDI) if applicable in format YYYYMMDD",
    to = toAccountTransferToAccountJson
  )

  val allFields =
    for (
      v <- this.getClass.getDeclaredFields
      //add guard, ignore the SwaggerJSONsV220.this and allFieldsAndValues fields
      if (APIUtil.notExstingBaseClass(v.getName()))
    )
      yield {
        v.setAccessible(true)
        v.get(this)
      }
}