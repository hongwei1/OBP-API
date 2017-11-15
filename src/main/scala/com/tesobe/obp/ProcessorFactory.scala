package com.tesobe.obp

import com.tesobe.obp.Main.{executionContext, materializer}
import com.tesobe.obp.SouthKafkaStreamsActor.BusinessTopic
import com.tesobe.obp.june2017._

/**
  * Defines kafka topics which will be used and functions that will be applied on received message
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
trait ProcessorFactory {
  this: Config =>

  /**
    *
    * @return sequence of functions which will be applied in processing of North Side messages
    */
  def getProcessor = {
    processorName match {
      case "localFile" => Seq(
        BusinessTopic(topic, LocalProcessor()(executionContext, materializer).generic),
        BusinessTopic(createTopicByClassName(OutboundGetBanks.getClass.getSimpleName), LocalProcessor()(executionContext, materializer).banksFn),
        BusinessTopic(createTopicByClassName(OutboundGetBank.getClass.getSimpleName), LocalProcessor()(executionContext, materializer).bankFn),
        BusinessTopic(createTopicByClassName(OutboundGetUserByUsernamePassword.getClass.getSimpleName), LocalProcessor()(executionContext, materializer).userFn),
        BusinessTopic(createTopicByClassName(OutboundGetAdapterInfo.getClass.getSimpleName), LocalProcessor()(executionContext, materializer).adapterFn),
        BusinessTopic(createTopicByClassName(OutboundGetAccountbyAccountID.getClass.getSimpleName), LocalProcessor()(executionContext, materializer).bankAccountIdFn),
        BusinessTopic(createTopicByClassName(OutboundCheckBankAccountExists.getClass.getSimpleName), LocalProcessor()(executionContext, materializer).checkBankAccountExistsFn),
        BusinessTopic(createTopicByClassName(OutboundGetAccounts.getClass.getSimpleName), LocalProcessor()(executionContext, materializer).bankAccountsFn),
        BusinessTopic(createTopicByClassName(OutboundGetTransactions.getClass.getSimpleName), LocalProcessor()(executionContext, materializer).transactionsFn),
        BusinessTopic(createTopicByClassName(OutboundGetTransaction.getClass.getSimpleName), LocalProcessor()(executionContext, materializer).transactionFn),
        BusinessTopic(createTopicByClassName(OutboundCreateTransaction.getClass.getSimpleName), LocalProcessor()(executionContext, materializer).createTransactionFn),
        BusinessTopic(createTopicByClassName(OutboundCreateChallengeJune2017.getClass.getSimpleName), LocalProcessor()(executionContext, materializer).createChallengeFn),
        BusinessTopic(createTopicByClassName(OutboundGetTransactionRequests210.getClass.getSimpleName), LocalProcessor()(executionContext, materializer).getTransactionRequestsFn),
        BusinessTopic(createTopicByClassName(OutboundCreateCounterparty.getClass.getSimpleName), LocalProcessor()(executionContext, materializer).createCounterpartyFn),
        BusinessTopic(createTopicByClassName(OutboundGetCoreBankAccounts.getClass.getSimpleName), LocalProcessor()(executionContext, materializer).getCoreBankAccountsFn),
        BusinessTopic(createTopicByClassName(OutboundGetCustomersByUserId.getClass.getSimpleName), LocalProcessor()(executionContext, materializer).getCustomerFn),
        BusinessTopic(createTopicByClassName(OutboundGetCounterparties.getClass.getSimpleName), LocalProcessor()(executionContext, materializer).getCounterpartiesFn),
        BusinessTopic(createTopicByClassName(OutboundGetCounterpartyByCounterpartyId.getClass.getSimpleName), LocalProcessor()(executionContext, materializer).getCounterpartyByIdFn)

      )
      case "mockedSopra" => BusinessTopic(topic, LocalProcessor()(executionContext, materializer).generic)
      case "sopra" => BusinessTopic(topic, LocalProcessor()(executionContext, materializer).generic)
      case _ => BusinessTopic(topic, LocalProcessor()(executionContext, materializer).generic)
    }
  }
}
