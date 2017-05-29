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
  by
  Simon Redfern : simon AT tesobe DOT com
  Stefan Bethge : stefan AT tesobe DOT com
  Everett Sochowski : everett AT tesobe DOT com
  Ayoub Benali: ayoub AT tesobe DOT com
  
  */
package code.api.v3_0_0

import code.api.ErrorMessage
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import _root_.net.liftweb.json.JsonAST.JObject
import _root_.net.liftweb.json.Serialization.write
import code.api.util.APIUtil.OAuth._
import code.api.v1_2._
import code.api.v2_2_0.{ViewJSONV220, ViewsJSONV220}
import code.model.{Consumer => OBPConsumer, Token => OBPToken, _}
import net.liftweb.json.JsonDSL._
import net.liftweb.util.Helpers._
import org.scalatest._

class API3_0_0Test extends V300ServerSetup{
  
  val view = createViewJson

  feature("List of the views of specific bank account - v3.0.0"){
    scenario("We will get the list of the available views on a bank account") {
      Given("We will use an access token")
      val bankId = mockBankId1.value
      val bankAccount : code.api.v1_2.AccountJSON = randomPrivateAccount(bankId)
      When("the request is sent")
      val reply = getAccountViews(bankId, bankAccount.id, user1)
      Then("we should get a 200 ok code")
      reply.code should equal (200)
      reply.body.extract[ViewsJsonV300]
    }

    scenario("We will not get the list of the available views on a bank account due to missing token") {
      Given("We will not use an access token")
      val bankId = mockBankId1.value
      val bankAccount : code.api.v1_2.AccountJSON = randomPrivateAccount(bankId)
      When("the request is sent")
      val reply = getAccountViews(bankId, bankAccount.id, None)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("We will not get the list of the available views on a bank account due to insufficient privileges") {
      Given("We will use an access token")
      val bankId = mockBankId1.value
      val bankAccount : code.api.v1_2.AccountJSON = randomPrivateAccount(bankId)
      When("the request is sent")
      val reply = getAccountViews(bankId, bankAccount.id, user3)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  }
  
  feature("Create a view on a bank account - v3.0.0"){
    scenario("we will create a view on a bank account") {
      Given("We will use an access token")
      val bankId = mockBankId1.value
      val bankAccount : code.api.v1_2.AccountJSON = randomPrivateAccount(bankId)
      val viewsBefore = getAccountViews(bankId, bankAccount.id, user1).body.extract[ViewsJsonV300].views

      When("the request is sent")
      val reply = postView(bankId, bankAccount.id, view, user1)
      Then("we should get a 201 code")
      reply.code should equal (201)
      reply.body.extract[ViewJSONV220]
      And("we should get a new view")
      val viewsAfter = getAccountViews(bankId, bankAccount.id, user1).body.extract[ViewsJsonV300].views
      viewsBefore.size should equal (viewsAfter.size -1)
    }

    scenario("We will not create a view on a bank account due to missing token") {
      Given("We will not use an access token")
      val bankId = mockBankId1.value
      val bankAccount : code.api.v1_2.AccountJSON = randomPrivateAccount(bankId)
      When("the request is sent")
      val reply = postView(bankId, bankAccount.id, view, None)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("We will not create a view on a bank account due to insufficient privileges") {
      Given("We will use an access token")
      val bankId = mockBankId1.value
      val bankAccount : code.api.v1_2.AccountJSON = randomPrivateAccount(bankId)
      When("the request is sent")
      val reply = postView(bankId, bankAccount.id, view, user3)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("We will not create a view because the bank account does not exist") {
      Given("We will use an access token")
      val bankId = mockBankId1.value
      When("the request is sent")
      val reply = postView(bankId, randomString(3), view, user1)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("We will not create a view because the view already exists") {
      Given("We will use an access token")
      val bankId = mockBankId1.value
      val bankAccount : code.api.v1_2.AccountJSON = randomPrivateAccount(bankId)
      postView(bankId, bankAccount.id, view, user1)
      When("the request is sent")
      val reply = postView(bankId, bankAccount.id, view, user1)
      Then("we should get a 400 code")
      reply.code should equal (400)
      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  }

  feature("Update a view on a bank account - v3.0.0") {

    val updatedViewDescription = "aloha"
    val updatedAliasToUse = "public"
    val allowedActions = List("can_see_images", "can_delete_comment")

    def viewUpdateJson(originalView : ViewJsonV300) = {
      //it's not perfect, assumes too much about originalView (i.e. randomView(true, ""))
      UpdateViewJSON(
        description = updatedViewDescription,
        is_public = !originalView.is_public,
        which_alias_to_use = updatedAliasToUse,
        hide_metadata_if_alias_used = !originalView.hide_metadata_if_alias_used,
        allowed_actions = allowedActions
      )
    }

    def someViewUpdateJson() = {
      UpdateViewJSON(
        description = updatedViewDescription,
        is_public = true,
        which_alias_to_use = updatedAliasToUse,
        hide_metadata_if_alias_used = true,
        allowed_actions = allowedActions
      )
    }

    scenario("we will update a view on a bank account") {
      Given("A view exists")
      val bankId = mockBankId1.value
      val bankAccount : code.api.v1_2.AccountJSON = randomPrivateAccount(bankId)
      val creationReply = postView(bankId, bankAccount.id, view, user1)
      creationReply.code should equal (201)
      val createdView : ViewJsonV300 = creationReply.body.extract[ViewJsonV300]
      createdView.can_see_images should equal(true)
      createdView.can_delete_comment should equal(true)
      createdView.can_delete_physical_location should equal(true)
      createdView.can_edit_owner_comment should equal(true)
      createdView.description should not equal(updatedViewDescription)
      createdView.is_public should equal(true)
      createdView.hide_metadata_if_alias_used should equal(false)

      When("We use a valid access token and valid put json")
      val reply = putView(bankId, bankAccount.id, createdView.id, viewUpdateJson(createdView), user1)
      Then("We should get back the updated view")
      reply.code should equal (200)
      val updatedView = reply.body.extract[ViewJsonV300]
      updatedView.can_see_images should equal(true)
      updatedView.can_delete_comment should equal(true)
      updatedView.can_delete_physical_location should equal(false)
      updatedView.can_edit_owner_comment should equal(false)
      updatedView.description should equal(updatedViewDescription)
      updatedView.is_public should equal(false)
      updatedView.hide_metadata_if_alias_used should equal(true)
    }

    scenario("we will not update a view that doesn't exist") {
      val bankId = mockBankId1.value
      val bankAccount : code.api.v1_2.AccountJSON = randomPrivateAccount(bankId)

      Given("a view does not exist")
      val nonExistantViewId = "asdfasdfasdfasdfasdf"
      val getReply = getAccountViews(bankId, bankAccount.id, user1)
      getReply.code should equal (200)
      val views : ViewsJSONV220 = getReply.body.extract[ViewsJSONV220]
      views.views.foreach(v => v.id should not equal(nonExistantViewId))

      When("we try to update that view")
      val reply = putView(bankId, bankAccount.id, nonExistantViewId, someViewUpdateJson(), user1)
      Then("We should get a 404")
      reply.code should equal(404)
    }

    scenario("We will not update a view on a bank account due to missing token") {
      Given("A view exists")
      val bankId = mockBankId1.value
      val bankAccount : code.api.v1_2.AccountJSON = randomPrivateAccount(bankId)
      val creationReply = postView(bankId, bankAccount.id, view, user1)
      creationReply.code should equal (201)
      val createdView : ViewJsonV300 = creationReply.body.extract[ViewJsonV300]

      When("we don't use an access token")
      val reply = putView(bankId, bankAccount.id, createdView.id, viewUpdateJson(createdView), None)
      Then("we should get a 400")
      reply.code should equal(400)

      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }

    scenario("we will not update a view on a bank account due to insufficient privileges") {
      Given("A view exists")
      val bankId = mockBankId1.value
      val bankAccount : code.api.v1_2.AccountJSON = randomPrivateAccount(bankId)
      val creationReply = postView(bankId, bankAccount.id, view, user1)
      creationReply.code should equal (201)
      val createdView : ViewJsonV300 = creationReply.body.extract[ViewJsonV300]

      When("we try to update a view without having sufficient privileges to do so")
      val reply = putView(bankId, bankAccount.id, createdView.id, viewUpdateJson(createdView), user3)
      Then("we should get a 400")
      reply.code should equal(400)

      And("we should get an error message")
      reply.body.extract[ErrorMessage].error.nonEmpty should equal (true)
    }
  }

}
