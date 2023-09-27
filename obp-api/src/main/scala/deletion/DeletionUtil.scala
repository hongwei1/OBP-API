package deletion

import net.liftweb.db.CustomDB
import net.liftweb.util.DefaultConnectionIdentifier

object DeletionUtil {
  def databaseAtomicTask[R](blockOfCode: => R): R = {
    CustomDB.use(DefaultConnectionIdentifier){_ =>
      blockOfCode
    }
  }
}
