package code.api.v4_0_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.jsonCodeTemplate
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import code.api.util.ErrorMessages.{UserNotLoggedIn, _}
import code.api.util.ExampleValue.endpointMappingRequestBodyExample
import code.api.v4_0_0.OBPAPI4_0_0.Implementations4_0_0
import code.endpointMapping.EndpointMappingCommons
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class EndpointMappingBankLevelTest extends V400ServerSetup {
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.createBankLevelEndpointMapping))
  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.getBankLevelEndpointMapping))
  object ApiEndpoint3 extends Tag(nameOf(Implementations4_0_0.getAllBankLevelEndpointMappings))
  object ApiEndpoint4 extends Tag(nameOf(Implementations4_0_0.updateBankLevelEndpointMapping))
  object ApiEndpoint5 extends Tag(nameOf(Implementations4_0_0.deleteBankLevelEndpointMapping))

  val rightEntity = endpointMappingRequestBodyExample
  val wrongEntity = jsonCodeTemplate
  
  feature("Add a EndpointMapping v4.0.0- Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "management" / "banks" / testBankId1.value  / "endpoint-mappings").POST

      val response400 = makePostRequest(request400, write(rightEntity))
      Then("We should get a 401")
      response400.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response400.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
  }
  feature("Update a EndpointMapping v4.0.0- Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint2, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "management" / "banks" / testBankId1.value  / "endpoint-mappings"/ "some-method-routing-id").PUT
      val response400 = makePutRequest(request400, write(rightEntity))
      Then("We should get a 401")
      response400.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response400.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
  }
  feature("Get EndpointMappings v4.0.0- Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint3, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "management" / "banks" / testBankId1.value  / "endpoint-mappings").GET  <<? (List(("method_name", "getBank")))
      val response400 = makeGetRequest(request400)
      Then("We should get a 401")
      response400.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response400.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
  }
  feature("Delete the EndpointMapping specified by METHOD_ROUTING_ID v4.0.0- Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint5, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "management" / "banks" / testBankId1.value  / "endpoint-mappings" / "METHOD_ROUTING_ID").DELETE
      val response400 = makeDeleteRequest(request400)
      Then("We should get a 401")
      response400.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response400.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
  }


  feature("Add a EndpointMapping v4.0.0- Unauthorized access - Authorized access") {
    scenario("We will call the endpoint without the proper Role " + CanCreateBankLevelEndpointMapping, ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0without a Role " + CanCreateBankLevelEndpointMapping)
      val request400 = (v4_0_0_Request / "management" / "banks" / testBankId1.value  / "endpoint-mappings").POST <@(user1)
      val response400 = makePostRequest(request400, write(rightEntity))
      Then("We should get a 403")
      response400.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanCreateBankLevelEndpointMapping)
      response400.body.extract[ErrorMessage].message contains  (UserHasMissingRoles )
      response400.body.extract[ErrorMessage].message contains  (CanCreateBankLevelEndpointMapping )
    }

    scenario("We will call the endpoint with the proper Role " + canCreateBankLevelEndpointMapping , ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, ApiEndpoint4, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, CanCreateBankLevelEndpointMapping.toString)
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "management" / "banks" / testBankId1.value  / "endpoint-mappings").POST <@(user1)
      val response400 = makePostRequest(request400, write(rightEntity))
      Then("We should get a 201")
      response400.code should equal(201)
      val customerJson = response400.body.extract[EndpointMappingCommons]

      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, CanUpdateBankLevelEndpointMapping.toString)
      When("We make a request v4.0.0with the Role " + canUpdateBankLevelEndpointMapping)

      {
        // update success
        val request400 = (v4_0_0_Request / "management" / "banks" / testBankId1.value  / "endpoint-mappings" / customerJson.endpointMappingId.get ).PUT <@(user1)
        val response400 = makePutRequest(request400, write(customerJson.copy(requestMapping = "{}")))
        Then("We should get a 201")
        response400.code should equal(201)
        val endpointMappingsJson = response400.body.extract[EndpointMappingCommons]
      }

      {
        // error case, cannot update with different operationid
        val request400 = (v4_0_0_Request / "management" / "banks" / testBankId1.value  / "endpoint-mappings" / customerJson.endpointMappingId.get ).PUT <@(user1)
        val response400 = makePutRequest(request400, write(customerJson.copy(operationId = "newOperationId")))
        Then("We should get a 400")
        response400.code should equal(400)
        val errorMessage = response400.body.extract[ErrorMessage].message
        errorMessage contains (s"$InvalidJsonFormat operation_id has to be the same in ") should be (true)
      }
      
      {
        // update a not exists EndpointMapping
        val request400 = (v4_0_0_Request / "management" / "banks" / testBankId1.value  / "endpoint-mappings" / "not-exists-id" ).PUT <@(user1)
        val response400 = makePutRequest(request400, write(customerJson.copy(operationId = "wrongId")))
        Then("We should get a 404")
        response400.code should equal(404)
        response400.body.extract[ErrorMessage].message should startWith (EndpointMappingNotFoundByEndpointMappingId)
      }

      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, CanGetAllBankLevelEndpointMappings.toString)
      When("We make a request v4.0.0with the Role " + canGetAllBankLevelEndpointMappings)
      val requestGet400 = (v4_0_0_Request / "management" / "banks" / testBankId1.value  / "endpoint-mappings").GET <@(user1) <<? (List(("method_name", "getBank")))
      val responseGet400 = makeGetRequest(requestGet400)
      Then("We should get a 200")
      responseGet400.code should equal(200)
      val json = responseGet400.body \ "endpoint-mappings"
      val endpointMappingsGetJson = json.extract[List[EndpointMappingCommons]]

      endpointMappingsGetJson.size should be (1)


      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, CanDeleteBankLevelEndpointMapping.toString)
      When("We make a request v4.0.0with the Role " + canDeleteBankLevelEndpointMapping)
      val requestDelete310 = (v4_0_0_Request / "management" / "banks" / testBankId1.value  / "endpoint-mappings" / endpointMappingsGetJson.head.endpointMappingId.get).DELETE <@(user1)
      val responseDelete310 = makeDeleteRequest(requestDelete310)
      Then("We should get a 200")
      responseDelete310.code should equal(200)
      
    }
  }
  
}
