// This is a generated file - do not edit.
//
// Generated from transport_files.proto.

// @dart = 3.3

// ignore_for_file: annotate_overrides, camel_case_types, comment_references
// ignore_for_file: constant_identifier_names
// ignore_for_file: curly_braces_in_flow_control_structures
// ignore_for_file: deprecated_member_use_from_same_package, library_prefixes
// ignore_for_file: non_constant_identifier_names, prefer_relative_imports
// ignore_for_file: unused_import

import 'dart:convert' as $convert;
import 'dart:core' as $core;
import 'dart:typed_data' as $typed_data;

@$core.Deprecated('Use fileChunkDescriptor instead')
const FileChunk$json = {
  '1': 'FileChunk',
  '2': [
    {'1': 'content', '3': 1, '4': 1, '5': 12, '10': 'content'},
    {'1': 'filename', '3': 2, '4': 1, '5': 9, '10': 'filename'},
    {'1': 'transfer_token', '3': 3, '4': 1, '5': 9, '10': 'transferToken'},
    {'1': 'sha256', '3': 4, '4': 1, '5': 12, '10': 'sha256'},
  ],
};

/// Descriptor for `FileChunk`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List fileChunkDescriptor = $convert.base64Decode(
    'CglGaWxlQ2h1bmsSGAoHY29udGVudBgBIAEoDFIHY29udGVudBIaCghmaWxlbmFtZRgCIAEoCV'
    'IIZmlsZW5hbWUSJQoOdHJhbnNmZXJfdG9rZW4YAyABKAlSDXRyYW5zZmVyVG9rZW4SFgoGc2hh'
    'MjU2GAQgASgMUgZzaGEyNTY=');

@$core.Deprecated('Use transferStatusDescriptor instead')
const TransferStatus$json = {
  '1': 'TransferStatus',
  '2': [
    {'1': 'success', '3': 1, '4': 1, '5': 8, '10': 'success'},
    {'1': 'message', '3': 2, '4': 1, '5': 9, '10': 'message'},
  ],
};

/// Descriptor for `TransferStatus`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List transferStatusDescriptor = $convert.base64Decode(
    'Cg5UcmFuc2ZlclN0YXR1cxIYCgdzdWNjZXNzGAEgASgIUgdzdWNjZXNzEhgKB21lc3NhZ2UYAi'
    'ABKAlSB21lc3NhZ2U=');

@$core.Deprecated('Use pairingRequestDescriptor instead')
const PairingRequest$json = {
  '1': 'PairingRequest',
  '2': [
    {'1': 'PCName', '3': 1, '4': 1, '5': 9, '10': 'PCName'},
    {'1': 'PCAddress', '3': 2, '4': 1, '5': 9, '10': 'PCAddress'},
    {'1': 'fileName', '3': 3, '4': 1, '5': 9, '10': 'fileName'},
    {'1': 'fileSize', '3': 4, '4': 1, '5': 9, '10': 'fileSize'},
    {'1': 'size_bytes', '3': 5, '4': 1, '5': 3, '10': 'sizeBytes'},
    {'1': 'protocol_version', '3': 6, '4': 1, '5': 5, '10': 'protocolVersion'},
    {
      '1': 'files',
      '3': 7,
      '4': 3,
      '5': 11,
      '6': '.kardash.project.proto.transport.FileInfo',
      '10': 'files'
    },
  ],
};

/// Descriptor for `PairingRequest`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List pairingRequestDescriptor = $convert.base64Decode(
    'Cg5QYWlyaW5nUmVxdWVzdBIWCgZQQ05hbWUYASABKAlSBlBDTmFtZRIcCglQQ0FkZHJlc3MYAi'
    'ABKAlSCVBDQWRkcmVzcxIaCghmaWxlTmFtZRgDIAEoCVIIZmlsZU5hbWUSGgoIZmlsZVNpemUY'
    'BCABKAlSCGZpbGVTaXplEh0KCnNpemVfYnl0ZXMYBSABKANSCXNpemVCeXRlcxIpChBwcm90b2'
    'NvbF92ZXJzaW9uGAYgASgFUg9wcm90b2NvbFZlcnNpb24SPwoFZmlsZXMYByADKAsyKS5rYXJk'
    'YXNoLnByb2plY3QucHJvdG8udHJhbnNwb3J0LkZpbGVJbmZvUgVmaWxlcw==');

@$core.Deprecated('Use fileInfoDescriptor instead')
const FileInfo$json = {
  '1': 'FileInfo',
  '2': [
    {'1': 'name', '3': 1, '4': 1, '5': 9, '10': 'name'},
    {'1': 'size', '3': 2, '4': 1, '5': 3, '10': 'size'},
  ],
};

/// Descriptor for `FileInfo`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List fileInfoDescriptor = $convert.base64Decode(
    'CghGaWxlSW5mbxISCgRuYW1lGAEgASgJUgRuYW1lEhIKBHNpemUYAiABKANSBHNpemU=');

@$core.Deprecated('Use pairingResponseDescriptor instead')
const PairingResponse$json = {
  '1': 'PairingResponse',
  '2': [
    {'1': 'response', '3': 1, '4': 1, '5': 8, '10': 'response'},
    {'1': 'transfer_token', '3': 2, '4': 1, '5': 9, '10': 'transferToken'},
  ],
};

/// Descriptor for `PairingResponse`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List pairingResponseDescriptor = $convert.base64Decode(
    'Cg9QYWlyaW5nUmVzcG9uc2USGgoIcmVzcG9uc2UYASABKAhSCHJlc3BvbnNlEiUKDnRyYW5zZm'
    'VyX3Rva2VuGAIgASgJUg10cmFuc2ZlclRva2Vu');

@$core.Deprecated('Use cancelRequestDescriptor instead')
const CancelRequest$json = {
  '1': 'CancelRequest',
};

/// Descriptor for `CancelRequest`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List cancelRequestDescriptor =
    $convert.base64Decode('Cg1DYW5jZWxSZXF1ZXN0');

@$core.Deprecated('Use cancelResponseDescriptor instead')
const CancelResponse$json = {
  '1': 'CancelResponse',
};

/// Descriptor for `CancelResponse`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List cancelResponseDescriptor =
    $convert.base64Decode('Cg5DYW5jZWxSZXNwb25zZQ==');
