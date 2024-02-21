// Generated by the Scala Plugin for the Protocol Buffer Compiler.
// Do not edit!
//
// Protofile syntax: PROTO3

package code.bankconnectors.grpc.api

@SerialVersionUID(0L)
final case class BankRoutingJsonV121Grpc(
    scheme: _root_.scala.Predef.String = "",
    address: _root_.scala.Predef.String = "",
    unknownFields: _root_.scalapb.UnknownFieldSet = _root_.scalapb.UnknownFieldSet.empty
    ) extends scalapb.GeneratedMessage with scalapb.lenses.Updatable[BankRoutingJsonV121Grpc] {
    @transient
    private[this] var __serializedSizeMemoized: _root_.scala.Int = 0
    private[this] def __computeSerializedSize(): _root_.scala.Int = {
      var __size = 0
      
      {
        val __value = scheme
        if (!__value.isEmpty) {
          __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(1, __value)
        }
      };
      
      {
        val __value = address
        if (!__value.isEmpty) {
          __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(2, __value)
        }
      };
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
        val __v = scheme
        if (!__v.isEmpty) {
          _output__.writeString(1, __v)
        }
      };
      {
        val __v = address
        if (!__v.isEmpty) {
          _output__.writeString(2, __v)
        }
      };
      unknownFields.writeTo(_output__)
    }
    def withScheme(__v: _root_.scala.Predef.String): BankRoutingJsonV121Grpc = copy(scheme = __v)
    def withAddress(__v: _root_.scala.Predef.String): BankRoutingJsonV121Grpc = copy(address = __v)
    def withUnknownFields(__v: _root_.scalapb.UnknownFieldSet) = copy(unknownFields = __v)
    def discardUnknownFields = copy(unknownFields = _root_.scalapb.UnknownFieldSet.empty)
    def getFieldByNumber(__fieldNumber: _root_.scala.Int): _root_.scala.Any = {
      (__fieldNumber: @_root_.scala.unchecked) match {
        case 1 => {
          val __t = scheme
          if (__t != "") __t else null
        }
        case 2 => {
          val __t = address
          if (__t != "") __t else null
        }
      }
    }
    def getField(__field: _root_.scalapb.descriptors.FieldDescriptor): _root_.scalapb.descriptors.PValue = {
      _root_.scala.Predef.require(__field.containingMessage eq companion.scalaDescriptor)
      (__field.number: @_root_.scala.unchecked) match {
        case 1 => _root_.scalapb.descriptors.PString(scheme)
        case 2 => _root_.scalapb.descriptors.PString(address)
      }
    }
    def toProtoString: _root_.scala.Predef.String = _root_.scalapb.TextFormat.printToUnicodeString(this)
    def companion: code.bankconnectors.grpc.api.BankRoutingJsonV121Grpc.type = code.bankconnectors.grpc.api.BankRoutingJsonV121Grpc
    // @@protoc_insertion_point(GeneratedMessage[code.bankconnectors.grpc.BankRoutingJsonV121Grpc])
}

object BankRoutingJsonV121Grpc extends scalapb.GeneratedMessageCompanion[code.bankconnectors.grpc.api.BankRoutingJsonV121Grpc] {
  implicit def messageCompanion: scalapb.GeneratedMessageCompanion[code.bankconnectors.grpc.api.BankRoutingJsonV121Grpc] = this
  def parseFrom(`_input__`: _root_.com.google.protobuf.CodedInputStream): code.bankconnectors.grpc.api.BankRoutingJsonV121Grpc = {
    var __scheme: _root_.scala.Predef.String = ""
    var __address: _root_.scala.Predef.String = ""
    var `_unknownFields__`: _root_.scalapb.UnknownFieldSet.Builder = null
    var _done__ = false
    while (!_done__) {
      val _tag__ = _input__.readTag()
      _tag__ match {
        case 0 => _done__ = true
        case 10 =>
          __scheme = _input__.readStringRequireUtf8()
        case 18 =>
          __address = _input__.readStringRequireUtf8()
        case tag =>
          if (_unknownFields__ == null) {
            _unknownFields__ = new _root_.scalapb.UnknownFieldSet.Builder()
          }
          _unknownFields__.parseField(tag, _input__)
      }
    }
    code.bankconnectors.grpc.api.BankRoutingJsonV121Grpc(
        scheme = __scheme,
        address = __address,
        unknownFields = if (_unknownFields__ == null) _root_.scalapb.UnknownFieldSet.empty else _unknownFields__.result()
    )
  }
  implicit def messageReads: _root_.scalapb.descriptors.Reads[code.bankconnectors.grpc.api.BankRoutingJsonV121Grpc] = _root_.scalapb.descriptors.Reads{
    case _root_.scalapb.descriptors.PMessage(__fieldsMap) =>
      _root_.scala.Predef.require(__fieldsMap.keys.forall(_.containingMessage eq scalaDescriptor), "FieldDescriptor does not match message type.")
      code.bankconnectors.grpc.api.BankRoutingJsonV121Grpc(
        scheme = __fieldsMap.get(scalaDescriptor.findFieldByNumber(1).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        address = __fieldsMap.get(scalaDescriptor.findFieldByNumber(2).get).map(_.as[_root_.scala.Predef.String]).getOrElse("")
      )
    case _ => throw new RuntimeException("Expected PMessage")
  }
  def javaDescriptor: _root_.com.google.protobuf.Descriptors.Descriptor = ApiProto.javaDescriptor.getMessageTypes().get(1)
  def scalaDescriptor: _root_.scalapb.descriptors.Descriptor = ApiProto.scalaDescriptor.messages(1)
  def messageCompanionForFieldNumber(__number: _root_.scala.Int): _root_.scalapb.GeneratedMessageCompanion[_] = throw new MatchError(__number)
  lazy val nestedMessagesCompanions: Seq[_root_.scalapb.GeneratedMessageCompanion[_ <: _root_.scalapb.GeneratedMessage]] = Seq.empty
  def enumCompanionForFieldNumber(__fieldNumber: _root_.scala.Int): _root_.scalapb.GeneratedEnumCompanion[_] = throw new MatchError(__fieldNumber)
  lazy val defaultInstance = code.bankconnectors.grpc.api.BankRoutingJsonV121Grpc(
    scheme = "",
    address = ""
  )
  implicit class BankRoutingJsonV121GrpcLens[UpperPB](_l: _root_.scalapb.lenses.Lens[UpperPB, code.bankconnectors.grpc.api.BankRoutingJsonV121Grpc]) extends _root_.scalapb.lenses.ObjectLens[UpperPB, code.bankconnectors.grpc.api.BankRoutingJsonV121Grpc](_l) {
    def scheme: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.scheme)((c_, f_) => c_.copy(scheme = f_))
    def address: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.address)((c_, f_) => c_.copy(address = f_))
  }
  final val SCHEME_FIELD_NUMBER = 1
  final val ADDRESS_FIELD_NUMBER = 2
  def of(
    scheme: _root_.scala.Predef.String,
    address: _root_.scala.Predef.String
  ): _root_.code.bankconnectors.grpc.api.BankRoutingJsonV121Grpc = _root_.code.bankconnectors.grpc.api.BankRoutingJsonV121Grpc(
    scheme,
    address
  )
  // @@protoc_insertion_point(GeneratedMessageCompanion[code.bankconnectors.grpc.BankRoutingJsonV121Grpc])
}
