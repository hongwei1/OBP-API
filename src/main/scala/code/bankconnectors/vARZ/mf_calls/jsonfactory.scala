package code.bankconnectors.vARZ.mf_calls

case class PostPrivatkundenkontakteRequest(
  uuid: String,
  kundenstamm: Kundenstamm,
  musterkundnr: Option[Int]
)

case class Kundenstamm(
  
  famname: String,
  vorname: String,
  mobiltel: String,
  emailadr: String,
  
  titel: Option[String],
  filiale: Option[String],
  titelnach: Option[String],
  kundnr: Option[Int],
  hilfszahl: Option[Int]
)

case class PostkundenkontakteResult(
  kundennummer: Int,
  messages: List[String],
  problem: Problem
)


case class Problem(
  title: String,
  detail: String,
  `type`: String,
  status: Int,
  errors: List[Error]
)

case class Error(
  title: String,
  detail: String,
  `type`: String
)

case class PostDisposersRequest(
  
  credentials: Credentials,
  status: String,
  language: String,
  `type`: String,
  customerNr: Int,
  address: DisposerAddress,
  bankSupervisorId: String,
  
  bankAdvisorId: Option[String],
  number: Option[Int],
)

case class Disposer(
  
  number: ARZValue,
  credentials: Credentials,
  status: String,
  language: String,
  `type`: String,
  customerNr: Int,
  address: DisposerAddress,
  bankSupervisorId: String,
  bankAdvisorId: String
)

case class Credentials(
  name: String,
  pin: Long
)

case class DisposerAddress(
  
  identifier: String,
  number: Int
)

case class PostDisposersResponse(
  value: Int
)

case class ARZValue(value: Int)
