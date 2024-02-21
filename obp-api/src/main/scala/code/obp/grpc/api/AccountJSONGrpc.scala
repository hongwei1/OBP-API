// Generated by the Scala Plugin for the Protocol Buffer Compiler.
// Do not edit!
//
// Protofile syntax: PROTO3

package code.obp.grpc.api

@SerialVersionUID(0L)
final case class AccountJSONGrpc(
    id: _root_.scala.Predef.String = "",
    label: _root_.scala.Predef.String = "",
    viewsAvailable: _root_.scala.Seq[code.obp.grpc.api.ViewsJSONV121Grpc] = _root_.scala.Seq.empty,
    bankId: _root_.scala.Predef.String = "",
    unknownFields: _root_.scalapb.UnknownFieldSet = _root_.scalapb.UnknownFieldSet.empty
    ) extends scalapb.GeneratedMessage with scalapb.lenses.Updatable[AccountJSONGrpc] {
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
        val __value = label
        if (!__value.isEmpty) {
          __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(2, __value)
        }
      };
      viewsAvailable.foreach { __item =>
        val __value = __item
        __size += 1 + _root_.com.google.protobuf.CodedOutputStream.computeUInt32SizeNoTag(__value.serializedSize) + __value.serializedSize
      }
      
      {
        val __value = bankId
        if (!__value.isEmpty) {
          __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(4, __value)
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
        val __v = id
        if (!__v.isEmpty) {
          _output__.writeString(1, __v)
        }
      };
      {
        val __v = label
        if (!__v.isEmpty) {
          _output__.writeString(2, __v)
        }
      };
      viewsAvailable.foreach { __v =>
        val __m = __v
        _output__.writeTag(3, 2)
        _output__.writeUInt32NoTag(__m.serializedSize)
        __m.writeTo(_output__)
      };
      {
        val __v = bankId
        if (!__v.isEmpty) {
          _output__.writeString(4, __v)
        }
      };
      unknownFields.writeTo(_output__)
    }
    def withId(__v: _root_.scala.Predef.String): AccountJSONGrpc = copy(id = __v)
    def withLabel(__v: _root_.scala.Predef.String): AccountJSONGrpc = copy(label = __v)
    def clearViewsAvailable = copy(viewsAvailable = _root_.scala.Seq.empty)
    def addViewsAvailable(__vs: code.obp.grpc.api.ViewsJSONV121Grpc *): AccountJSONGrpc = addAllViewsAvailable(__vs)
    def addAllViewsAvailable(__vs: Iterable[code.obp.grpc.api.ViewsJSONV121Grpc]): AccountJSONGrpc = copy(viewsAvailable = viewsAvailable ++ __vs)
    def withViewsAvailable(__v: _root_.scala.Seq[code.obp.grpc.api.ViewsJSONV121Grpc]): AccountJSONGrpc = copy(viewsAvailable = __v)
    def withBankId(__v: _root_.scala.Predef.String): AccountJSONGrpc = copy(bankId = __v)
    def withUnknownFields(__v: _root_.scalapb.UnknownFieldSet) = copy(unknownFields = __v)
    def discardUnknownFields = copy(unknownFields = _root_.scalapb.UnknownFieldSet.empty)
    def getFieldByNumber(__fieldNumber: _root_.scala.Int): _root_.scala.Any = {
      (__fieldNumber: @_root_.scala.unchecked) match {
        case 1 => {
          val __t = id
          if (__t != "") __t else null
        }
        case 2 => {
          val __t = label
          if (__t != "") __t else null
        }
        case 3 => viewsAvailable
        case 4 => {
          val __t = bankId
          if (__t != "") __t else null
        }
      }
    }
    def getField(__field: _root_.scalapb.descriptors.FieldDescriptor): _root_.scalapb.descriptors.PValue = {
      _root_.scala.Predef.require(__field.containingMessage eq companion.scalaDescriptor)
      (__field.number: @_root_.scala.unchecked) match {
        case 1 => _root_.scalapb.descriptors.PString(id)
        case 2 => _root_.scalapb.descriptors.PString(label)
        case 3 => _root_.scalapb.descriptors.PRepeated(viewsAvailable.iterator.map(_.toPMessage).toVector)
        case 4 => _root_.scalapb.descriptors.PString(bankId)
      }
    }
    def toProtoString: _root_.scala.Predef.String = _root_.scalapb.TextFormat.printToUnicodeString(this)
    def companion: code.obp.grpc.api.AccountJSONGrpc.type = code.obp.grpc.api.AccountJSONGrpc
    // @@protoc_insertion_point(GeneratedMessage[code.obp.grpc.AccountJSONGrpc])
}

object AccountJSONGrpc extends scalapb.GeneratedMessageCompanion[code.obp.grpc.api.AccountJSONGrpc] {
  implicit def messageCompanion: scalapb.GeneratedMessageCompanion[code.obp.grpc.api.AccountJSONGrpc] = this
  def parseFrom(`_input__`: _root_.com.google.protobuf.CodedInputStream): code.obp.grpc.api.AccountJSONGrpc = {
    var __id: _root_.scala.Predef.String = ""
    var __label: _root_.scala.Predef.String = ""
    val __viewsAvailable: _root_.scala.collection.immutable.VectorBuilder[code.obp.grpc.api.ViewsJSONV121Grpc] = new _root_.scala.collection.immutable.VectorBuilder[code.obp.grpc.api.ViewsJSONV121Grpc]
    var __bankId: _root_.scala.Predef.String = ""
    var `_unknownFields__`: _root_.scalapb.UnknownFieldSet.Builder = null
    var _done__ = false
    while (!_done__) {
      val _tag__ = _input__.readTag()
      _tag__ match {
        case 0 => _done__ = true
        case 10 =>
          __id = _input__.readStringRequireUtf8()
        case 18 =>
          __label = _input__.readStringRequireUtf8()
        case 26 =>
          __viewsAvailable += _root_.scalapb.LiteParser.readMessage[code.obp.grpc.api.ViewsJSONV121Grpc](_input__)
        case 34 =>
          __bankId = _input__.readStringRequireUtf8()
        case tag =>
          if (_unknownFields__ == null) {
            _unknownFields__ = new _root_.scalapb.UnknownFieldSet.Builder()
          }
          _unknownFields__.parseField(tag, _input__)
      }
    }
    code.obp.grpc.api.AccountJSONGrpc(
        id = __id,
        label = __label,
        viewsAvailable = __viewsAvailable.result(),
        bankId = __bankId,
        unknownFields = if (_unknownFields__ == null) _root_.scalapb.UnknownFieldSet.empty else _unknownFields__.result()
    )
  }
  implicit def messageReads: _root_.scalapb.descriptors.Reads[code.obp.grpc.api.AccountJSONGrpc] = _root_.scalapb.descriptors.Reads{
    case _root_.scalapb.descriptors.PMessage(__fieldsMap) =>
      _root_.scala.Predef.require(__fieldsMap.keys.forall(_.containingMessage eq scalaDescriptor), "FieldDescriptor does not match message type.")
      code.obp.grpc.api.AccountJSONGrpc(
        id = __fieldsMap.get(scalaDescriptor.findFieldByNumber(1).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        label = __fieldsMap.get(scalaDescriptor.findFieldByNumber(2).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        viewsAvailable = __fieldsMap.get(scalaDescriptor.findFieldByNumber(3).get).map(_.as[_root_.scala.Seq[code.obp.grpc.api.ViewsJSONV121Grpc]]).getOrElse(_root_.scala.Seq.empty),
        bankId = __fieldsMap.get(scalaDescriptor.findFieldByNumber(4).get).map(_.as[_root_.scala.Predef.String]).getOrElse("")
      )
    case _ => throw new RuntimeException("Expected PMessage")
  }
  def javaDescriptor: _root_.com.google.protobuf.Descriptors.Descriptor = ApiProto.javaDescriptor.getMessageTypes().get(2)
  def scalaDescriptor: _root_.scalapb.descriptors.Descriptor = ApiProto.scalaDescriptor.messages(2)
  def messageCompanionForFieldNumber(__number: _root_.scala.Int): _root_.scalapb.GeneratedMessageCompanion[_] = {
    var __out: _root_.scalapb.GeneratedMessageCompanion[_] = null
    (__number: @_root_.scala.unchecked) match {
      case 3 => __out = code.obp.grpc.api.ViewsJSONV121Grpc
    }
    __out
  }
  lazy val nestedMessagesCompanions: Seq[_root_.scalapb.GeneratedMessageCompanion[_ <: _root_.scalapb.GeneratedMessage]] = Seq.empty
  def enumCompanionForFieldNumber(__fieldNumber: _root_.scala.Int): _root_.scalapb.GeneratedEnumCompanion[_] = throw new MatchError(__fieldNumber)
  lazy val defaultInstance = code.obp.grpc.api.AccountJSONGrpc(
    id = "",
    label = "",
    viewsAvailable = _root_.scala.Seq.empty,
    bankId = ""
  )
  implicit class AccountJSONGrpcLens[UpperPB](_l: _root_.scalapb.lenses.Lens[UpperPB, code.obp.grpc.api.AccountJSONGrpc]) extends _root_.scalapb.lenses.ObjectLens[UpperPB, code.obp.grpc.api.AccountJSONGrpc](_l) {
    def id: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.id)((c_, f_) => c_.copy(id = f_))
    def label: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.label)((c_, f_) => c_.copy(label = f_))
    def viewsAvailable: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Seq[code.obp.grpc.api.ViewsJSONV121Grpc]] = field(_.viewsAvailable)((c_, f_) => c_.copy(viewsAvailable = f_))
    def bankId: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.bankId)((c_, f_) => c_.copy(bankId = f_))
  }
  final val ID_FIELD_NUMBER = 1
  final val LABEL_FIELD_NUMBER = 2
  final val VIEWS_AVAILABLE_FIELD_NUMBER = 3
  final val BANK_ID_FIELD_NUMBER = 4
  def of(
    id: _root_.scala.Predef.String,
    label: _root_.scala.Predef.String,
    viewsAvailable: _root_.scala.Seq[code.obp.grpc.api.ViewsJSONV121Grpc],
    bankId: _root_.scala.Predef.String
  ): _root_.code.obp.grpc.api.AccountJSONGrpc = _root_.code.obp.grpc.api.AccountJSONGrpc(
    id,
    label,
    viewsAvailable,
    bankId
  )
  // @@protoc_insertion_point(GeneratedMessageCompanion[code.obp.grpc.AccountJSONGrpc])
}
