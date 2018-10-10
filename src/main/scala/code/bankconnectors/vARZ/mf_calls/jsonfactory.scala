package code.bankconnectors.vARZ.mf_calls

case class PostPrivatkundenkontakteRequest(
musterkundnr:  Int,
kundenstamm: Kundenstamm,
uuid: String
 )

case class Kundenstamm(
famname: String,
vorname: String,
titel: String, 
mobiltel: String,
emailadr: String,
filiale: String,
titelnach: String,
kundnr: Int,
hilfszahl: Int
                      )

case class PostkundenkontakteResult(
  kundennummer: Int,
  messages: List[String],
  problem: Problem)
    

case class Problem(
    title: String,
    detail: String,
    `type`: String,
    status: Int,
    errId: String,
    errors: List[Error] )
case class Error(
  title: String,
  detail: String,
  `type`: String
)

case class PostDisposersRequest(

                                 number: Int,
                                 credentials: Credentials,
                                 status: String,
                                 language: String,
                                 `type`: String,
                                 customerNr: Int,
                                 address: DisposerAddress,
                                 bankSupervisorId: String,
                                 bankAdvisorId: String
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
  pin: String
                      )

case class DisposerAddress(

  identifier: String,
  number: Int
                  )

case class PostDisposersResponse(
                                value: Int
                                )

case class ARZValue ( value: Int)
