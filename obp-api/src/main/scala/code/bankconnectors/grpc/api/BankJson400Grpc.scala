// Generated by the Scala Plugin for the Protocol Buffer Compiler.
// Do not edit!
//
// Protofile syntax: PROTO3

package code.bankconnectors.grpc.api

@SerialVersionUID(0L)
final case class BankJson400Grpc(
    id: _root_.scala.Predef.String = "",
    shortName: _root_.scala.Predef.String = "",
    fullName: _root_.scala.Predef.String = "",
    logo: _root_.scala.Predef.String = "",
    website: _root_.scala.Predef.String = "",
    bankRoutings: _root_.scala.Seq[code.bankconnectors.grpc.api.BankRoutingJsonV121Grpc] = _root_.scala.Seq.empty,
    unknownFields: _root_.scalapb.UnknownFieldSet = _root_.scalapb.UnknownFieldSet.empty
    ) extends scalapb.GeneratedMessage with scalapb.lenses.Updatable[BankJson400Grpc] {
    @transient
    private[this] var __serializedSizeMemoized: _root_.scala.Int = 0
    private[this] def __computeSerializedSize(): _root_.scala.Int = {
      var __size = 0
      
      {
        val __value = id
        if (!__value.isEmpty) {
          __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(1, __value)
        }
      };
      
      {
        val __value = shortName
        if (!__value.isEmpty) {
          __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(2, __value)
        }
      };
      
      {
        val __value = fullName
        if (!__value.isEmpty) {
          __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(3, __value)
        }
      };
      
      {
        val __value = logo
        if (!__value.isEmpty) {
          __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(4, __value)
        }
      };
      
      {
        val __value = website
        if (!__value.isEmpty) {
          __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(5, __value)
        }
      };
      bankRoutings.foreach { __item =>
        val __value = __item
        __size += 1 + _root_.com.google.protobuf.CodedOutputStream.computeUInt32SizeNoTag(__value.serializedSize) + __value.serializedSize
      }
      __size += unknownFields.serializedSize
      __size
    }
    override def serializedSize: _root_.scala.Int = {
      var __size = __serializedSizeMemoized
      if (__size == 0) {
        __size = __computeSerializedSize() + 1
        __serializedSizeMemoized = __size
      }
      __size - 1
      
    }
    def writeTo(`_output__`: _root_.com.google.protobuf.CodedOutputStream): _root_.scala.Unit = {
      {
        val __v = id
        if (!__v.isEmpty) {
          _output__.writeString(1, __v)
        }
      };
      {
        val __v = shortName
        if (!__v.isEmpty) {
          _output__.writeString(2, __v)
        }
      };
      {
        val __v = fullName
        if (!__v.isEmpty) {
          _output__.writeString(3, __v)
        }
      };
      {
        val __v = logo
        if (!__v.isEmpty) {
          _output__.writeString(4, __v)
        }
      };
      {
        val __v = website
        if (!__v.isEmpty) {
          _output__.writeString(5, __v)
        }
      };
      bankRoutings.foreach { __v =>
        val __m = __v
        _output__.writeTag(6, 2)
        _output__.writeUInt32NoTag(__m.serializedSize)
        __m.writeTo(_output__)
      };
      unknownFields.writeTo(_output__)
    }
    def withId(__v: _root_.scala.Predef.String): BankJson400Grpc = copy(id = __v)
    def withShortName(__v: _root_.scala.Predef.String): BankJson400Grpc = copy(shortName = __v)
    def withFullName(__v: _root_.scala.Predef.String): BankJson400Grpc = copy(fullName = __v)
    def withLogo(__v: _root_.scala.Predef.String): BankJson400Grpc = copy(logo = __v)
    def withWebsite(__v: _root_.scala.Predef.String): BankJson400Grpc = copy(website = __v)
    def clearBankRoutings = copy(bankRoutings = _root_.scala.Seq.empty)
    def addBankRoutings(__vs: code.bankconnectors.grpc.api.BankRoutingJsonV121Grpc *): BankJson400Grpc = addAllBankRoutings(__vs)
    def addAllBankRoutings(__vs: Iterable[code.bankconnectors.grpc.api.BankRoutingJsonV121Grpc]): BankJson400Grpc = copy(bankRoutings = bankRoutings ++ __vs)
    def withBankRoutings(__v: _root_.scala.Seq[code.bankconnectors.grpc.api.BankRoutingJsonV121Grpc]): BankJson400Grpc = copy(bankRoutings = __v)
    def withUnknownFields(__v: _root_.scalapb.UnknownFieldSet) = copy(unknownFields = __v)
    def discardUnknownFields = copy(unknownFields = _root_.scalapb.UnknownFieldSet.empty)
    def getFieldByNumber(__fieldNumber: _root_.scala.Int): _root_.scala.Any = {
      (__fieldNumber: @_root_.scala.unchecked) match {
        case 1 => {
          val __t = id
          if (__t != "") __t else null
        }
        case 2 => {
          val __t = shortName
          if (__t != "") __t else null
        }
        case 3 => {
          val __t = fullName
          if (__t != "") __t else null
        }
        case 4 => {
          val __t = logo
          if (__t != "") __t else null
        }
        case 5 => {
          val __t = website
          if (__t != "") __t else null
        }
        case 6 => bankRoutings
      }
    }
    def getField(__field: _root_.scalapb.descriptors.FieldDescriptor): _root_.scalapb.descriptors.PValue = {
      _root_.scala.Predef.require(__field.containingMessage eq companion.scalaDescriptor)
      (__field.number: @_root_.scala.unchecked) match {
        case 1 => _root_.scalapb.descriptors.PString(id)
        case 2 => _root_.scalapb.descriptors.PString(shortName)
        case 3 => _root_.scalapb.descriptors.PString(fullName)
        case 4 => _root_.scalapb.descriptors.PString(logo)
        case 5 => _root_.scalapb.descriptors.PString(website)
        case 6 => _root_.scalapb.descriptors.PRepeated(bankRoutings.iterator.map(_.toPMessage).toVector)
      }
    }
    def toProtoString: _root_.scala.Predef.String = _root_.scalapb.TextFormat.printToUnicodeString(this)
    def companion: code.bankconnectors.grpc.api.BankJson400Grpc.type = code.bankconnectors.grpc.api.BankJson400Grpc
    // @@protoc_insertion_point(GeneratedMessage[code.bankconnectors.grpc.BankJson400Grpc])
}

object BankJson400Grpc extends scalapb.GeneratedMessageCompanion[code.bankconnectors.grpc.api.BankJson400Grpc] {
  implicit def messageCompanion: scalapb.GeneratedMessageCompanion[code.bankconnectors.grpc.api.BankJson400Grpc] = this
  def parseFrom(`_input__`: _root_.com.google.protobuf.CodedInputStream): code.bankconnectors.grpc.api.BankJson400Grpc = {
    var __id: _root_.scala.Predef.String = ""
    var __shortName: _root_.scala.Predef.String = ""
    var __fullName: _root_.scala.Predef.String = ""
    var __logo: _root_.scala.Predef.String = ""
    var __website: _root_.scala.Predef.String = ""
    val __bankRoutings: _root_.scala.collection.immutable.VectorBuilder[code.bankconnectors.grpc.api.BankRoutingJsonV121Grpc] = new _root_.scala.collection.immutable.VectorBuilder[code.bankconnectors.grpc.api.BankRoutingJsonV121Grpc]
    var `_unknownFields__`: _root_.scalapb.UnknownFieldSet.Builder = null
    var _done__ = false
    while (!_done__) {
      val _tag__ = _input__.readTag()
      _tag__ match {
        case 0 => _done__ = true
        case 10 =>
          __id = _input__.readStringRequireUtf8()
        case 18 =>
          __shortName = _input__.readStringRequireUtf8()
        case 26 =>
          __fullName = _input__.readStringRequireUtf8()
        case 34 =>
          __logo = _input__.readStringRequireUtf8()
        case 42 =>
          __website = _input__.readStringRequireUtf8()
        case 50 =>
          __bankRoutings += _root_.scalapb.LiteParser.readMessage[code.bankconnectors.grpc.api.BankRoutingJsonV121Grpc](_input__)
        case tag =>
          if (_unknownFields__ == null) {
            _unknownFields__ = new _root_.scalapb.UnknownFieldSet.Builder()
          }
          _unknownFields__.parseField(tag, _input__)
      }
    }
    code.bankconnectors.grpc.api.BankJson400Grpc(
        id = __id,
        shortName = __shortName,
        fullName = __fullName,
        logo = __logo,
        website = __website,
        bankRoutings = __bankRoutings.result(),
        unknownFields = if (_unknownFields__ == null) _root_.scalapb.UnknownFieldSet.empty else _unknownFields__.result()
    )
  }
  implicit def messageReads: _root_.scalapb.descriptors.Reads[code.bankconnectors.grpc.api.BankJson400Grpc] = _root_.scalapb.descriptors.Reads{
    case _root_.scalapb.descriptors.PMessage(__fieldsMap) =>
      _root_.scala.Predef.require(__fieldsMap.keys.forall(_.containingMessage eq scalaDescriptor), "FieldDescriptor does not match message type.")
      code.bankconnectors.grpc.api.BankJson400Grpc(
        id = __fieldsMap.get(scalaDescriptor.findFieldByNumber(1).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        shortName = __fieldsMap.get(scalaDescriptor.findFieldByNumber(2).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        fullName = __fieldsMap.get(scalaDescriptor.findFieldByNumber(3).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        logo = __fieldsMap.get(scalaDescriptor.findFieldByNumber(4).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        website = __fieldsMap.get(scalaDescriptor.findFieldByNumber(5).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        bankRoutings = __fieldsMap.get(scalaDescriptor.findFieldByNumber(6).get).map(_.as[_root_.scala.Seq[code.bankconnectors.grpc.api.BankRoutingJsonV121Grpc]]).getOrElse(_root_.scala.Seq.empty)
      )
    case _ => throw new RuntimeException("Expected PMessage")
  }
  def javaDescriptor: _root_.com.google.protobuf.Descriptors.Descriptor = ApiProto.javaDescriptor.getMessageTypes().get(2)
  def scalaDescriptor: _root_.scalapb.descriptors.Descriptor = ApiProto.scalaDescriptor.messages(2)
  def messageCompanionForFieldNumber(__number: _root_.scala.Int): _root_.scalapb.GeneratedMessageCompanion[_] = {
    var __out: _root_.scalapb.GeneratedMessageCompanion[_] = null
    (__number: @_root_.scala.unchecked) match {
      case 6 => __out = code.bankconnectors.grpc.api.BankRoutingJsonV121Grpc
    }
    __out
  }
  lazy val nestedMessagesCompanions: Seq[_root_.scalapb.GeneratedMessageCompanion[_ <: _root_.scalapb.GeneratedMessage]] = Seq.empty
  def enumCompanionForFieldNumber(__fieldNumber: _root_.scala.Int): _root_.scalapb.GeneratedEnumCompanion[_] = throw new MatchError(__fieldNumber)
  lazy val defaultInstance = code.bankconnectors.grpc.api.BankJson400Grpc(
    id = "",
    shortName = "",
    fullName = "",
    logo = "",
    website = "",
    bankRoutings = _root_.scala.Seq.empty
  )
  implicit class BankJson400GrpcLens[UpperPB](_l: _root_.scalapb.lenses.Lens[UpperPB, code.bankconnectors.grpc.api.BankJson400Grpc]) extends _root_.scalapb.lenses.ObjectLens[UpperPB, code.bankconnectors.grpc.api.BankJson400Grpc](_l) {
    def id: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.id)((c_, f_) => c_.copy(id = f_))
    def shortName: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.shortName)((c_, f_) => c_.copy(shortName = f_))
    def fullName: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.fullName)((c_, f_) => c_.copy(fullName = f_))
    def logo: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.logo)((c_, f_) => c_.copy(logo = f_))
    def website: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.website)((c_, f_) => c_.copy(website = f_))
    def bankRoutings: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Seq[code.bankconnectors.grpc.api.BankRoutingJsonV121Grpc]] = field(_.bankRoutings)((c_, f_) => c_.copy(bankRoutings = f_))
  }
  final val ID_FIELD_NUMBER = 1
  final val SHORT_NAME_FIELD_NUMBER = 2
  final val FULL_NAME_FIELD_NUMBER = 3
  final val LOGO_FIELD_NUMBER = 4
  final val WEBSITE_FIELD_NUMBER = 5
  final val BANK_ROUTINGS_FIELD_NUMBER = 6
  def of(
    id: _root_.scala.Predef.String,
    shortName: _root_.scala.Predef.String,
    fullName: _root_.scala.Predef.String,
    logo: _root_.scala.Predef.String,
    website: _root_.scala.Predef.String,
    bankRoutings: _root_.scala.Seq[code.bankconnectors.grpc.api.BankRoutingJsonV121Grpc]
  ): _root_.code.bankconnectors.grpc.api.BankJson400Grpc = _root_.code.bankconnectors.grpc.api.BankJson400Grpc(
    id,
    shortName,
    fullName,
    logo,
    website,
    bankRoutings
  )
  // @@protoc_insertion_point(GeneratedMessageCompanion[code.bankconnectors.grpc.BankJson400Grpc])
}
