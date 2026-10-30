// This is a generated file - do not edit.
//
// Generated from transport_files.proto.

// @dart = 3.3

// ignore_for_file: annotate_overrides, camel_case_types, comment_references
// ignore_for_file: constant_identifier_names
// ignore_for_file: curly_braces_in_flow_control_structures
// ignore_for_file: deprecated_member_use_from_same_package, library_prefixes
// ignore_for_file: non_constant_identifier_names, prefer_relative_imports

import 'dart:core' as $core;

import 'package:fixnum/fixnum.dart' as $fixnum;
import 'package:protobuf/protobuf.dart' as $pb;

export 'package:protobuf/protobuf.dart' show GeneratedMessageGenericExtensions;

class FileChunk extends $pb.GeneratedMessage {
  factory FileChunk({
    $core.List<$core.int>? content,
    $core.String? filename,
    $core.String? transferToken,
    $core.List<$core.int>? sha256,
  }) {
    final result = FileChunk._();
    if (content != null) result.content = content;
    if (filename != null) result.filename = filename;
    if (transferToken != null) result.transferToken = transferToken;
    if (sha256 != null) result.sha256 = sha256;
    return result;
  }

  FileChunk._();

  factory FileChunk.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      FileChunk()..mergeFromBuffer(data, registry);
  factory FileChunk.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      FileChunk()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'FileChunk',
      package: const $pb.PackageName(
          _omitMessageNames ? '' : 'kardash.project.proto.transport'),
      createEmptyInstance: FileChunk.$_createMessage)
    ..a<$core.List<$core.int>>(
        1, _omitFieldNames ? '' : 'content', $pb.PbFieldType.OY)
    ..aOS(2, _omitFieldNames ? '' : 'filename')
    ..aOS(3, _omitFieldNames ? '' : 'transferToken')
    ..a<$core.List<$core.int>>(
        4, _omitFieldNames ? '' : 'sha256', $pb.PbFieldType.OY)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  FileChunk clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  FileChunk copyWith(void Function(FileChunk) updates) =>
      super.copyWith((message) => updates(message as FileChunk)) as FileChunk;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use FileChunk() / FileChunk.new instead')
  static FileChunk create() => FileChunk._();
  static $pb.GeneratedMessage $_createMessage() => FileChunk._();
  @$core.override
  FileChunk createEmptyInstance() => FileChunk._();
  @$core.pragma('dart2js:noInline')
  static FileChunk getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<FileChunk>(FileChunk.$_createMessage);
  static FileChunk? _defaultInstance;

  @$pb.TagNumber(1)
  $core.List<$core.int> get content => $_getN(0);
  @$pb.TagNumber(1)
  set content($core.List<$core.int> value) => $_setBytes(0, value);
  @$pb.TagNumber(1)
  $core.bool hasContent() => $_has(0);
  @$pb.TagNumber(1)
  void clearContent() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get filename => $_getSZ(1);
  @$pb.TagNumber(2)
  set filename($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasFilename() => $_has(1);
  @$pb.TagNumber(2)
  void clearFilename() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.String get transferToken => $_getSZ(2);
  @$pb.TagNumber(3)
  set transferToken($core.String value) => $_setString(2, value);
  @$pb.TagNumber(3)
  $core.bool hasTransferToken() => $_has(2);
  @$pb.TagNumber(3)
  void clearTransferToken() => $_clearField(3);

  @$pb.TagNumber(4)
  $core.List<$core.int> get sha256 => $_getN(3);
  @$pb.TagNumber(4)
  set sha256($core.List<$core.int> value) => $_setBytes(3, value);
  @$pb.TagNumber(4)
  $core.bool hasSha256() => $_has(3);
  @$pb.TagNumber(4)
  void clearSha256() => $_clearField(4);
}

class TransferStatus extends $pb.GeneratedMessage {
  factory TransferStatus({
    $core.bool? success,
    $core.String? message,
  }) {
    final result = TransferStatus._();
    if (success != null) result.success = success;
    if (message != null) result.message = message;
    return result;
  }

  TransferStatus._();

  factory TransferStatus.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TransferStatus()..mergeFromBuffer(data, registry);
  factory TransferStatus.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TransferStatus()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'TransferStatus',
      package: const $pb.PackageName(
          _omitMessageNames ? '' : 'kardash.project.proto.transport'),
      createEmptyInstance: TransferStatus.$_createMessage)
    ..aOB(1, _omitFieldNames ? '' : 'success')
    ..aOS(2, _omitFieldNames ? '' : 'message')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TransferStatus clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TransferStatus copyWith(void Function(TransferStatus) updates) =>
      super.copyWith((message) => updates(message as TransferStatus))
          as TransferStatus;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use TransferStatus() / TransferStatus.new instead')
  static TransferStatus create() => TransferStatus._();
  static $pb.GeneratedMessage $_createMessage() => TransferStatus._();
  @$core.override
  TransferStatus createEmptyInstance() => TransferStatus._();
  @$core.pragma('dart2js:noInline')
  static TransferStatus getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<TransferStatus>(
          TransferStatus.$_createMessage);
  static TransferStatus? _defaultInstance;

  @$pb.TagNumber(1)
  $core.bool get success => $_getBF(0);
  @$pb.TagNumber(1)
  set success($core.bool value) => $_setBool(0, value);
  @$pb.TagNumber(1)
  $core.bool hasSuccess() => $_has(0);
  @$pb.TagNumber(1)
  void clearSuccess() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get message => $_getSZ(1);
  @$pb.TagNumber(2)
  set message($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasMessage() => $_has(1);
  @$pb.TagNumber(2)
  void clearMessage() => $_clearField(2);
}

class PairingRequest extends $pb.GeneratedMessage {
  factory PairingRequest({
    $core.String? pCName,
    $core.String? pCAddress,
    $core.String? fileName,
    $core.String? fileSize,
    $fixnum.Int64? sizeBytes,
    $core.int? protocolVersion,
    $core.Iterable<FileInfo>? files,
  }) {
    final result = PairingRequest._();
    if (pCName != null) result.pCName = pCName;
    if (pCAddress != null) result.pCAddress = pCAddress;
    if (fileName != null) result.fileName = fileName;
    if (fileSize != null) result.fileSize = fileSize;
    if (sizeBytes != null) result.sizeBytes = sizeBytes;
    if (protocolVersion != null) result.protocolVersion = protocolVersion;
    if (files != null) result.files.addAll(files);
    return result;
  }

  PairingRequest._();

  factory PairingRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      PairingRequest()..mergeFromBuffer(data, registry);
  factory PairingRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      PairingRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'PairingRequest',
      package: const $pb.PackageName(
          _omitMessageNames ? '' : 'kardash.project.proto.transport'),
      createEmptyInstance: PairingRequest.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'PCName', protoName: 'PCName')
    ..aOS(2, _omitFieldNames ? '' : 'PCAddress', protoName: 'PCAddress')
    ..aOS(3, _omitFieldNames ? '' : 'fileName', protoName: 'fileName')
    ..aOS(4, _omitFieldNames ? '' : 'fileSize', protoName: 'fileSize')
    ..aInt64(5, _omitFieldNames ? '' : 'sizeBytes')
    ..aI(6, _omitFieldNames ? '' : 'protocolVersion')
    ..pPM<FileInfo>(7, _omitFieldNames ? '' : 'files',
        subBuilder: FileInfo.$_createMessage)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  PairingRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  PairingRequest copyWith(void Function(PairingRequest) updates) =>
      super.copyWith((message) => updates(message as PairingRequest))
          as PairingRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use PairingRequest() / PairingRequest.new instead')
  static PairingRequest create() => PairingRequest._();
  static $pb.GeneratedMessage $_createMessage() => PairingRequest._();
  @$core.override
  PairingRequest createEmptyInstance() => PairingRequest._();
  @$core.pragma('dart2js:noInline')
  static PairingRequest getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<PairingRequest>(
          PairingRequest.$_createMessage);
  static PairingRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get pCName => $_getSZ(0);
  @$pb.TagNumber(1)
  set pCName($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasPCName() => $_has(0);
  @$pb.TagNumber(1)
  void clearPCName() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get pCAddress => $_getSZ(1);
  @$pb.TagNumber(2)
  set pCAddress($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasPCAddress() => $_has(1);
  @$pb.TagNumber(2)
  void clearPCAddress() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.String get fileName => $_getSZ(2);
  @$pb.TagNumber(3)
  set fileName($core.String value) => $_setString(2, value);
  @$pb.TagNumber(3)
  $core.bool hasFileName() => $_has(2);
  @$pb.TagNumber(3)
  void clearFileName() => $_clearField(3);

  @$pb.TagNumber(4)
  $core.String get fileSize => $_getSZ(3);
  @$pb.TagNumber(4)
  set fileSize($core.String value) => $_setString(3, value);
  @$pb.TagNumber(4)
  $core.bool hasFileSize() => $_has(3);
  @$pb.TagNumber(4)
  void clearFileSize() => $_clearField(4);

  @$pb.TagNumber(5)
  $fixnum.Int64 get sizeBytes => $_getI64(4);
  @$pb.TagNumber(5)
  set sizeBytes($fixnum.Int64 value) => $_setInt64(4, value);
  @$pb.TagNumber(5)
  $core.bool hasSizeBytes() => $_has(4);
  @$pb.TagNumber(5)
  void clearSizeBytes() => $_clearField(5);

  @$pb.TagNumber(6)
  $core.int get protocolVersion => $_getIZ(5);
  @$pb.TagNumber(6)
  set protocolVersion($core.int value) => $_setSignedInt32(5, value);
  @$pb.TagNumber(6)
  $core.bool hasProtocolVersion() => $_has(5);
  @$pb.TagNumber(6)
  void clearProtocolVersion() => $_clearField(6);

  @$pb.TagNumber(7)
  $pb.PbList<FileInfo> get files => $_getList(6);
}

class FileInfo extends $pb.GeneratedMessage {
  factory FileInfo({
    $core.String? name,
    $fixnum.Int64? size,
  }) {
    final result = FileInfo._();
    if (name != null) result.name = name;
    if (size != null) result.size = size;
    return result;
  }

  FileInfo._();

  factory FileInfo.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      FileInfo()..mergeFromBuffer(data, registry);
  factory FileInfo.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      FileInfo()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'FileInfo',
      package: const $pb.PackageName(
          _omitMessageNames ? '' : 'kardash.project.proto.transport'),
      createEmptyInstance: FileInfo.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'name')
    ..aInt64(2, _omitFieldNames ? '' : 'size')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  FileInfo clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  FileInfo copyWith(void Function(FileInfo) updates) =>
      super.copyWith((message) => updates(message as FileInfo)) as FileInfo;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use FileInfo() / FileInfo.new instead')
  static FileInfo create() => FileInfo._();
  static $pb.GeneratedMessage $_createMessage() => FileInfo._();
  @$core.override
  FileInfo createEmptyInstance() => FileInfo._();
  @$core.pragma('dart2js:noInline')
  static FileInfo getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<FileInfo>(FileInfo.$_createMessage);
  static FileInfo? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get name => $_getSZ(0);
  @$pb.TagNumber(1)
  set name($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasName() => $_has(0);
  @$pb.TagNumber(1)
  void clearName() => $_clearField(1);

  @$pb.TagNumber(2)
  $fixnum.Int64 get size => $_getI64(1);
  @$pb.TagNumber(2)
  set size($fixnum.Int64 value) => $_setInt64(1, value);
  @$pb.TagNumber(2)
  $core.bool hasSize() => $_has(1);
  @$pb.TagNumber(2)
  void clearSize() => $_clearField(2);
}

class PairingResponse extends $pb.GeneratedMessage {
  factory PairingResponse({
    $core.bool? response,
    $core.String? transferToken,
  }) {
    final result = PairingResponse._();
    if (response != null) result.response = response;
    if (transferToken != null) result.transferToken = transferToken;
    return result;
  }

  PairingResponse._();

  factory PairingResponse.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      PairingResponse()..mergeFromBuffer(data, registry);
  factory PairingResponse.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      PairingResponse()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'PairingResponse',
      package: const $pb.PackageName(
          _omitMessageNames ? '' : 'kardash.project.proto.transport'),
      createEmptyInstance: PairingResponse.$_createMessage)
    ..aOB(1, _omitFieldNames ? '' : 'response')
    ..aOS(2, _omitFieldNames ? '' : 'transferToken')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  PairingResponse clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  PairingResponse copyWith(void Function(PairingResponse) updates) =>
      super.copyWith((message) => updates(message as PairingResponse))
          as PairingResponse;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use PairingResponse() / PairingResponse.new instead')
  static PairingResponse create() => PairingResponse._();
  static $pb.GeneratedMessage $_createMessage() => PairingResponse._();
  @$core.override
  PairingResponse createEmptyInstance() => PairingResponse._();
  @$core.pragma('dart2js:noInline')
  static PairingResponse getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<PairingResponse>(
          PairingResponse.$_createMessage);
  static PairingResponse? _defaultInstance;

  @$pb.TagNumber(1)
  $core.bool get response => $_getBF(0);
  @$pb.TagNumber(1)
  set response($core.bool value) => $_setBool(0, value);
  @$pb.TagNumber(1)
  $core.bool hasResponse() => $_has(0);
  @$pb.TagNumber(1)
  void clearResponse() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get transferToken => $_getSZ(1);
  @$pb.TagNumber(2)
  set transferToken($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasTransferToken() => $_has(1);
  @$pb.TagNumber(2)
  void clearTransferToken() => $_clearField(2);
}

class CancelRequest extends $pb.GeneratedMessage {
  factory CancelRequest() => CancelRequest._();

  CancelRequest._();

  factory CancelRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      CancelRequest()..mergeFromBuffer(data, registry);
  factory CancelRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      CancelRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'CancelRequest',
      package: const $pb.PackageName(
          _omitMessageNames ? '' : 'kardash.project.proto.transport'),
      createEmptyInstance: CancelRequest.$_createMessage)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  CancelRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  CancelRequest copyWith(void Function(CancelRequest) updates) =>
      super.copyWith((message) => updates(message as CancelRequest))
          as CancelRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use CancelRequest() / CancelRequest.new instead')
  static CancelRequest create() => CancelRequest._();
  static $pb.GeneratedMessage $_createMessage() => CancelRequest._();
  @$core.override
  CancelRequest createEmptyInstance() => CancelRequest._();
  @$core.pragma('dart2js:noInline')
  static CancelRequest getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<CancelRequest>(
          CancelRequest.$_createMessage);
  static CancelRequest? _defaultInstance;
}

class CancelResponse extends $pb.GeneratedMessage {
  factory CancelResponse() => CancelResponse._();

  CancelResponse._();

  factory CancelResponse.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      CancelResponse()..mergeFromBuffer(data, registry);
  factory CancelResponse.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      CancelResponse()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'CancelResponse',
      package: const $pb.PackageName(
          _omitMessageNames ? '' : 'kardash.project.proto.transport'),
      createEmptyInstance: CancelResponse.$_createMessage)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  CancelResponse clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  CancelResponse copyWith(void Function(CancelResponse) updates) =>
      super.copyWith((message) => updates(message as CancelResponse))
          as CancelResponse;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use CancelResponse() / CancelResponse.new instead')
  static CancelResponse create() => CancelResponse._();
  static $pb.GeneratedMessage $_createMessage() => CancelResponse._();
  @$core.override
  CancelResponse createEmptyInstance() => CancelResponse._();
  @$core.pragma('dart2js:noInline')
  static CancelResponse getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<CancelResponse>(
          CancelResponse.$_createMessage);
  static CancelResponse? _defaultInstance;
}

const $core.bool _omitFieldNames =
    $core.bool.fromEnvironment('protobuf.omit_field_names');
const $core.bool _omitMessageNames =
    $core.bool.fromEnvironment('protobuf.omit_message_names');
