#!/bin/bash
sed -i '' -e '/else if (APIUtil.`hasConsent-ID`(reqHeaders)) { \/\/ Berlin Group'\''s Consent/,/Consent.applyBerlinGroupRules(APIUtil.`getConsent-ID`(reqHeaders), cc.copy(consumer = consumerForConsent))/!b' \
-e '/Consent.applyBerlinGroupRules/c\
        val consentIdOpt = APIUtil.`getConsent-ID`(reqHeaders)\
        val isUKConsentOpt = consentIdOpt.flatMap(id => code.consents.Consents.consentProvider.vend.getConsentByConsentId(id).toOption)\
        isUKConsentOpt match {\
          case Some(c) if Option(c.apiStandard).getOrElse("").startsWith("UKOpenBanking") =>\
            val user = code.users.Users.users.vend.getUserByUserId(c.userId)\
            scala.concurrent.Future { (user, Some(cc.copy(consumer = consumerForConsent))) }\
          case _ =>\
            Consent.applyBerlinGroupRules(consentIdOpt, cc.copy(consumer = consumerForConsent))\
        }\
' /Users/zhanghongwei/Documents/GitHub-Tower/OBP-API/obp-api/src/main/scala/code/api/util/APIUtil.scala
