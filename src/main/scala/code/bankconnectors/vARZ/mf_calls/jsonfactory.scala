package code.bankconnectors.vARZ.mf_calls

case class PostPrivatkundenkontakteRequest(
  uuid: String,
  kundenstamm: Kundenstamm,
)

case class Kundenstamm(
  famname: String,
  vorname: String,
  mobiltel: String,
  emailadr: String,
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

case class Kundennummer(kundennummer: String)

case class Konto(
  kontonummer: Long,
  iban: String,
  geschaeftsart: String,
  produktbezeichnung: String,
  produktnummer: String,
  produktvariante: String,
  saldo: Int,
  waehrung: String,
  kontobezeichnung: String,
  gemeinschaftsprodukt: Boolean,
  kundennummerhilfsstamm: Option[Long]
)


case class KundennummerForGet(
  bigDecimalValue: Long,
  value: Long
)
case class Strasse(
  value: String
)
case class GetKundenResponseJson(
  kundennummer: KundennummerForGet,
  active: Boolean,
  name1: String,
  name2: String,
  strasse: Strasse,
  ort: Strasse,
  postleitzahl: Strasse,
  datenqualitaet: String,
  rechtsform: Strasse,
  betreuer: Strasse,
  filiale: Strasse,
  emailadresse: Option[Strasse],
  geburtsdatum:Option[Strasse]
)