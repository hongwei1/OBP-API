package code

import com.tesobe.obp.Encryption.{encryptToken, decryptToken}
import org.scalatest.{FunSuite, Matchers}


/**
  * Created by work on 6/12/17.
  */
class EncryptionTest extends FunSuite with Matchers{
  
  test("encryptToken works"){
    val encryptedToken = encryptToken(">,?          81433020102612")
   encryptedToken should be (">,?          81433020102612")
  }
  
  test("decryptToken works"){
    val decryptedToken = decryptToken(">,?          81433020102612")
    decryptedToken should be (">,?          81433020102612")
  }

}
