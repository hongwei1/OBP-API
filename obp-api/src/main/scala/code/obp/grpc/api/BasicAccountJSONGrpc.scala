// Generated by the Scala Plugin for the Protocol Buffer Compiler.
// Do not edit!
//
// Protofile syntax: PROTO3

package code.obp.grpc.api

@SerialVersionUID(0L)
final case class BasicAccountJSONGrpc(
    id: _root_.scala.Predef.String = "",
    label: _root_.scala.Predef.String = "",
    bankId: _root_.scala.Predef.String = "",
    viewsAvailable: _root_.scala.Seq[code.obp.grpc.api.BasicAccountJSONGrpc.BasicViewJson] = _root_.scala.Seq.empty,
    unknownFields: _root_.scalapb.UnknownFieldSet = _root_.scalapb.UnknownFieldSet.empty
    ) extends scalapb.GeneratedMessage with scalapb.lenses.Updatable[BasicAccountJSONGrpc] {
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
      
      {
        val __value = bankId
        if (!__value.isEmpty) {
          __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(3, __value)
        }
      };
      viewsAvailable.foreach { __item =>
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
        val __v = label
        if (!__v.isEmpty) {
          _output__.writeString(2, __v)
        }
      };
      {
        val __v = bankId
        if (!__v.isEmpty) {
          _output__.writeString(3, __v)
        }
      };
      viewsAvailable.foreach { __v =>
        val __m = __v
        _output__.writeTag(4, 2)
        _output__.writeUInt32NoTag(__m.serializedSize)
        __m.writeTo(_output__)
      };
      unknownFields.writeTo(_output__)
    }
    def withId(__v: _root_.scala.Predef.String): BasicAccountJSONGrpc = copy(id = __v)
    def withLabel(__v: _root_.scala.Predef.String): BasicAccountJSONGrpc = copy(label = __v)
    def withBankId(__v: _root_.scala.Predef.String): BasicAccountJSONGrpc = copy(bankId = __v)
    def clearViewsAvailable = copy(viewsAvailable = _root_.scala.Seq.empty)
    def addViewsAvailable(__vs: code.obp.grpc.api.BasicAccountJSONGrpc.BasicViewJson *): BasicAccountJSONGrpc = addAllViewsAvailable(__vs)
    def addAllViewsAvailable(__vs: Iterable[code.obp.grpc.api.BasicAccountJSONGrpc.BasicViewJson]): BasicAccountJSONGrpc = copy(viewsAvailable = viewsAvailable ++ __vs)
    def withViewsAvailable(__v: _root_.scala.Seq[code.obp.grpc.api.BasicAccountJSONGrpc.BasicViewJson]): BasicAccountJSONGrpc = copy(viewsAvailable = __v)
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
        case 3 => {
          val __t = bankId
          if (__t != "") __t else null
        }
        case 4 => viewsAvailable
      }
    }
    def getField(__field: _root_.scalapb.descriptors.FieldDescriptor): _root_.scalapb.descriptors.PValue = {
      _root_.scala.Predef.require(__field.containingMessage eq companion.scalaDescriptor)
      (__field.number: @_root_.scala.unchecked) match {
        case 1 => _root_.scalapb.descriptors.PString(id)
        case 2 => _root_.scalapb.descriptors.PString(label)
        case 3 => _root_.scalapb.descriptors.PString(bankId)
        case 4 => _root_.scalapb.descriptors.PRepeated(viewsAvailable.iterator.map(_.toPMessage).toVector)
      }
    }
    def toProtoString: _root_.scala.Predef.String = _root_.scalapb.TextFormat.printToUnicodeString(this)
    def companion: code.obp.grpc.api.BasicAccountJSONGrpc.type = code.obp.grpc.api.BasicAccountJSONGrpc
    // @@protoc_insertion_point(GeneratedMessage[code.obp.grpc.BasicAccountJSONGrpc])
}

object BasicAccountJSONGrpc extends scalapb.GeneratedMessageCompanion[code.obp.grpc.api.BasicAccountJSONGrpc] {
  implicit def messageCompanion: scalapb.GeneratedMessageCompanion[code.obp.grpc.api.BasicAccountJSONGrpc] = this
  def parseFrom(`_input__`: _root_.com.google.protobuf.CodedInputStream): code.obp.grpc.api.BasicAccountJSONGrpc = {
    var __id: _root_.scala.Predef.String = ""
    var __label: _root_.scala.Predef.String = ""
    var __bankId: _root_.scala.Predef.String = ""
    val __viewsAvailable: _root_.scala.collection.immutable.VectorBuilder[code.obp.grpc.api.BasicAccountJSONGrpc.BasicViewJson] = new _root_.scala.collection.immutable.VectorBuilder[code.obp.grpc.api.BasicAccountJSONGrpc.BasicViewJson]
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
          __bankId = _input__.readStringRequireUtf8()
        case 34 =>
          __viewsAvailable += _root_.scalapb.LiteParser.readMessage[code.obp.grpc.api.BasicAccountJSONGrpc.BasicViewJson](_input__)
        case tag =>
          if (_unknownFields__ == null) {
            _unknownFields__ = new _root_.scalapb.UnknownFieldSet.Builder()
          }
          _unknownFields__.parseField(tag, _input__)
      }
    }
    code.obp.grpc.api.BasicAccountJSONGrpc(
        id = __id,
        label = __label,
        bankId = __bankId,
        viewsAvailable = __viewsAvailable.result(),
        unknownFields = if (_unknownFields__ == null) _root_.scalapb.UnknownFieldSet.empty else _unknownFields__.result()
    )
  }
  implicit def messageReads: _root_.scalapb.descriptors.Reads[code.obp.grpc.api.BasicAccountJSONGrpc] = _root_.scalapb.descriptors.Reads{
    case _root_.scalapb.descriptors.PMessage(__fieldsMap) =>
      _root_.scala.Predef.require(__fieldsMap.keys.forall(_.containingMessage eq scalaDescriptor), "FieldDescriptor does not match message type.")
      code.obp.grpc.api.BasicAccountJSONGrpc(
        id = __fieldsMap.get(scalaDescriptor.findFieldByNumber(1).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        label = __fieldsMap.get(scalaDescriptor.findFieldByNumber(2).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        bankId = __fieldsMap.get(scalaDescriptor.findFieldByNumber(3).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        viewsAvailable = __fieldsMap.get(scalaDescriptor.findFieldByNumber(4).get).map(_.as[_root_.scala.Seq[code.obp.grpc.api.BasicAccountJSONGrpc.BasicViewJson]]).getOrElse(_root_.scala.Seq.empty)
      )
    case _ => throw new RuntimeException("Expected PMessage")
  }
  def javaDescriptor: _root_.com.google.protobuf.Descriptors.Descriptor = ApiProto.javaDescriptor.getMessageTypes().get(6)
  def scalaDescriptor: _root_.scalapb.descriptors.Descriptor = ApiProto.scalaDescriptor.messages(6)
  def messageCompanionForFieldNumber(__number: _root_.scala.Int): _root_.scalapb.GeneratedMessageCompanion[_] = {
    var __out: _root_.scalapb.GeneratedMessageCompanion[_] = null
    (__number: @_root_.scala.unchecked) match {
      case 4 => __out = code.obp.grpc.api.BasicAccountJSONGrpc.BasicViewJson
    }
    __out
  }
  lazy val nestedMessagesCompanions: Seq[_root_.scalapb.GeneratedMessageCompanion[_ <: _root_.scalapb.GeneratedMessage]] =
    Seq[_root_.scalapb.GeneratedMessageCompanion[_ <: _root_.scalapb.GeneratedMessage]](
      _root_.code.obp.grpc.api.BasicAccountJSONGrpc.BasicViewJson
    )
  def enumCompanionForFieldNumber(__fieldNumber: _root_.scala.Int): _root_.scalapb.GeneratedEnumCompanion[_] = throw new MatchError(__fieldNumber)
  lazy val defaultInstance = code.obp.grpc.api.BasicAccountJSONGrpc(
    id = "",
    label = "",
    bankId = "",
    viewsAvailable = _root_.scala.Seq.empty
  )
  @SerialVersionUID(0L)
  final case class BasicViewJson(
      id: _root_.scala.Predef.String = "",
      shortName: _root_.scala.Predef.String = "",
      isPublic: _root_.scala.Boolean = false,
      unknownFields: _root_.scalapb.UnknownFieldSet = _root_.scalapb.UnknownFieldSet.empty
      ) extends scalapb.GeneratedMessage with scalapb.lenses.Updatable[BasicViewJson] {
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
          val __value = isPublic
          if (__value != false) {
            __size += _root_.com.google.protobuf.CodedOutputStream.computeBoolSize(3, __value)
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
          val __v = shortName
          if (!__v.isEmpty) {
            _output__.writeString(2, __v)
          }
        };
        {
          val __v = isPublic
          if (__v != false) {
            _output__.writeBool(3, __v)
          }
        };
        unknownFields.writeTo(_output__)
      }
      def withId(__v: _root_.scala.Predef.String): BasicViewJson = copy(id = __v)
      def withShortName(__v: _root_.scala.Predef.String): BasicViewJson = copy(shortName = __v)
      def withIsPublic(__v: _root_.scala.Boolean): BasicViewJson = copy(isPublic = __v)
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
            val __t = isPublic
            if (__t != false) __t else null
          }
        }
      }
      def getField(__field: _root_.scalapb.descriptors.FieldDescriptor): _root_.scalapb.descriptors.PValue = {
        _root_.scala.Predef.require(__field.containingMessage eq companion.scalaDescriptor)
        (__field.number: @_root_.scala.unchecked) match {
          case 1 => _root_.scalapb.descriptors.PString(id)
          case 2 => _root_.scalapb.descriptors.PString(shortName)
          case 3 => _root_.scalapb.descriptors.PBoolean(isPublic)
        }
      }
      def toProtoString: _root_.scala.Predef.String = _root_.scalapb.TextFormat.printToUnicodeString(this)
      def companion: code.obp.grpc.api.BasicAccountJSONGrpc.BasicViewJson.type = code.obp.grpc.api.BasicAccountJSONGrpc.BasicViewJson
      // @@protoc_insertion_point(GeneratedMessage[code.obp.grpc.BasicAccountJSONGrpc.BasicViewJson])
  }
  
  object BasicViewJson extends scalapb.GeneratedMessageCompanion[code.obp.grpc.api.BasicAccountJSONGrpc.BasicViewJson] {
    implicit def messageCompanion: scalapb.GeneratedMessageCompanion[code.obp.grpc.api.BasicAccountJSONGrpc.BasicViewJson] = this
    def parseFrom(`_input__`: _root_.com.google.protobuf.CodedInputStream): code.obp.grpc.api.BasicAccountJSONGrpc.BasicViewJson = {
      var __id: _root_.scala.Predef.String = ""
      var __shortName: _root_.scala.Predef.String = ""
      var __isPublic: _root_.scala.Boolean = false
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
          case 24 =>
            __isPublic = _input__.readBool()
          case tag =>
            if (_unknownFields__ == null) {
              _unknownFields__ = new _root_.scalapb.UnknownFieldSet.Builder()
            }
            _unknownFields__.parseField(tag, _input__)
        }
      }
      code.obp.grpc.api.BasicAccountJSONGrpc.BasicViewJson(
          id = __id,
          shortName = __shortName,
          isPublic = __isPublic,
          unknownFields = if (_unknownFields__ == null) _root_.scalapb.UnknownFieldSet.empty else _unknownFields__.result()
      )
    }
    implicit def messageReads: _root_.scalapb.descriptors.Reads[code.obp.grpc.api.BasicAccountJSONGrpc.BasicViewJson] = _root_.scalapb.descriptors.Reads{
      case _root_.scalapb.descriptors.PMessage(__fieldsMap) =>
        _root_.scala.Predef.require(__fieldsMap.keys.forall(_.containingMessage eq scalaDescriptor), "FieldDescriptor does not match message type.")
        code.obp.grpc.api.BasicAccountJSONGrpc.BasicViewJson(
          id = __fieldsMap.get(scalaDescriptor.findFieldByNumber(1).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
          shortName = __fieldsMap.get(scalaDescriptor.findFieldByNumber(2).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
          isPublic = __fieldsMap.get(scalaDescriptor.findFieldByNumber(3).get).map(_.as[_root_.scala.Boolean]).getOrElse(false)
        )
      case _ => throw new RuntimeException("Expected PMessage")
    }
    def javaDescriptor: _root_.com.google.protobuf.Descriptors.Descriptor = code.obp.grpc.api.BasicAccountJSONGrpc.javaDescriptor.getNestedTypes().get(0)
    def scalaDescriptor: _root_.scalapb.descriptors.Descriptor = code.obp.grpc.api.BasicAccountJSONGrpc.scalaDescriptor.nestedMessages(0)
    def messageCompanionForFieldNumber(__number: _root_.scala.Int): _root_.scalapb.GeneratedMessageCompanion[_] = throw new MatchError(__number)
    lazy val nestedMessagesCompanions: Seq[_root_.scalapb.GeneratedMessageCompanion[_ <: _root_.scalapb.GeneratedMessage]] = Seq.empty
    def enumCompanionForFieldNumber(__fieldNumber: _root_.scala.Int): _root_.scalapb.GeneratedEnumCompanion[_] = throw new MatchError(__fieldNumber)
    lazy val defaultInstance = code.obp.grpc.api.BasicAccountJSONGrpc.BasicViewJson(
      id = "",
      shortName = "",
      isPublic = false
    )
    implicit class BasicViewJsonLens[UpperPB](_l: _root_.scalapb.lenses.Lens[UpperPB, code.obp.grpc.api.BasicAccountJSONGrpc.BasicViewJson]) extends _root_.scalapb.lenses.ObjectLens[UpperPB, code.obp.grpc.api.BasicAccountJSONGrpc.BasicViewJson](_l) {
      def id: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.id)((c_, f_) => c_.copy(id = f_))
      def shortName: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.shortName)((c_, f_) => c_.copy(shortName = f_))
      def isPublic: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Boolean] = field(_.isPublic)((c_, f_) => c_.copy(isPublic = f_))
    }
    final val ID_FIELD_NUMBER = 1
    final val SHORT_NAME_FIELD_NUMBER = 2
    final val IS_PUBLIC_FIELD_NUMBER = 3
    def of(
      id: _root_.scala.Predef.String,
      shortName: _root_.scala.Predef.String,
      isPublic: _root_.scala.Boolean
    ): _root_.code.obp.grpc.api.BasicAccountJSONGrpc.BasicViewJson = _root_.code.obp.grpc.api.BasicAccountJSONGrpc.BasicViewJson(
      id,
      shortName,
      isPublic
    )
    // @@protoc_insertion_point(GeneratedMessageCompanion[code.obp.grpc.BasicAccountJSONGrpc.BasicViewJson])
  }
  
  implicit class BasicAccountJSONGrpcLens[UpperPB](_l: _root_.scalapb.lenses.Lens[UpperPB, code.obp.grpc.api.BasicAccountJSONGrpc]) extends _root_.scalapb.lenses.ObjectLens[UpperPB, code.obp.grpc.api.BasicAccountJSONGrpc](_l) {
    def id: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.id)((c_, f_) => c_.copy(id = f_))
    def label: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.label)((c_, f_) => c_.copy(label = f_))
    def bankId: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.bankId)((c_, f_) => c_.copy(bankId = f_))
    def viewsAvailable: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Seq[code.obp.grpc.api.BasicAccountJSONGrpc.BasicViewJson]] = field(_.viewsAvailable)((c_, f_) => c_.copy(viewsAvailable = f_))
  }
  final val ID_FIELD_NUMBER = 1
  final val LABEL_FIELD_NUMBER = 2
  final val BANK_ID_FIELD_NUMBER = 3
  final val VIEWS_AVAILABLE_FIELD_NUMBER = 4
  def of(
    id: _root_.scala.Predef.String,
    label: _root_.scala.Predef.String,
    bankId: _root_.scala.Predef.String,
    viewsAvailable: _root_.scala.Seq[code.obp.grpc.api.BasicAccountJSONGrpc.BasicViewJson]
  ): _root_.code.obp.grpc.api.BasicAccountJSONGrpc = _root_.code.obp.grpc.api.BasicAccountJSONGrpc(
    id,
    label,
    bankId,
    viewsAvailable
  )
  // @@protoc_insertion_point(GeneratedMessageCompanion[code.obp.grpc.BasicAccountJSONGrpc])
}
