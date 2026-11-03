// This is a generated file - do not edit.
//
// Generated from transport_files.proto.

// @dart = 3.3

// ignore_for_file: annotate_overrides, camel_case_types, comment_references
// ignore_for_file: constant_identifier_names
// ignore_for_file: curly_braces_in_flow_control_structures
// ignore_for_file: deprecated_member_use_from_same_package, library_prefixes
// ignore_for_file: non_constant_identifier_names, prefer_relative_imports

import 'dart:async' as $async;
import 'dart:core' as $core;

import 'package:grpc/service_api.dart' as $grpc;
import 'package:protobuf/protobuf.dart' as $pb;

import 'transport_files.pb.dart' as $0;

export 'transport_files.pb.dart';

@$pb.GrpcServiceName('kardash.project.proto.transport.FileTransferService')
class FileTransferServiceClient extends $grpc.Client {
  /// The hostname for this service.
  static const $core.String defaultHost = '';

  /// OAuth scopes needed for the client.
  static const $core.List<$core.String> oauthScopes = [
    '',
  ];

  FileTransferServiceClient(super.channel, {super.options, super.interceptors});

  $grpc.ResponseFuture<$0.TransferStatus> sendFile(
    $async.Stream<$0.FileChunk> request, {
    $grpc.CallOptions? options,
  }) {
    return $createStreamingCall(_$sendFile, request, options: options).single;
  }

  // method descriptors

  static final _$sendFile = $grpc.ClientMethod<$0.FileChunk, $0.TransferStatus>(
      '/kardash.project.proto.transport.FileTransferService/sendFile',
      ($0.FileChunk value) => value.writeToBuffer(),
      $0.TransferStatus.fromBuffer);
}

@$pb.GrpcServiceName('kardash.project.proto.transport.FileTransferService')
abstract class FileTransferServiceBase extends $grpc.Service {
  $core.String get $name =>
      'kardash.project.proto.transport.FileTransferService';

  FileTransferServiceBase() {
    $addMethod($grpc.ServiceMethod<$0.FileChunk, $0.TransferStatus>(
        'sendFile',
        sendFile,
        true,
        false,
        ($core.List<$core.int> value) => $0.FileChunk.fromBuffer(value),
        ($0.TransferStatus value) => value.writeToBuffer()));
  }

  $async.Future<$0.TransferStatus> sendFile(
      $grpc.ServiceCall call, $async.Stream<$0.FileChunk> request);
}

@$pb.GrpcServiceName('kardash.project.proto.transport.PairingService')
class PairingServiceClient extends $grpc.Client {
  /// The hostname for this service.
  static const $core.String defaultHost = '';

  /// OAuth scopes needed for the client.
  static const $core.List<$core.String> oauthScopes = [
    '',
  ];

  PairingServiceClient(super.channel, {super.options, super.interceptors});

  $grpc.ResponseFuture<$0.PairingResponse> pairingRequest(
    $0.PairingRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$pairingRequest, request, options: options);
  }

  // method descriptors

  static final _$pairingRequest =
      $grpc.ClientMethod<$0.PairingRequest, $0.PairingResponse>(
          '/kardash.project.proto.transport.PairingService/pairingRequest',
          ($0.PairingRequest value) => value.writeToBuffer(),
          $0.PairingResponse.fromBuffer);
}

@$pb.GrpcServiceName('kardash.project.proto.transport.PairingService')
abstract class PairingServiceBase extends $grpc.Service {
  $core.String get $name => 'kardash.project.proto.transport.PairingService';

  PairingServiceBase() {
    $addMethod($grpc.ServiceMethod<$0.PairingRequest, $0.PairingResponse>(
        'pairingRequest',
        pairingRequest_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.PairingRequest.fromBuffer(value),
        ($0.PairingResponse value) => value.writeToBuffer()));
  }

  $async.Future<$0.PairingResponse> pairingRequest_Pre($grpc.ServiceCall $call,
      $async.Future<$0.PairingRequest> $request) async {
    return pairingRequest($call, await $request);
  }

  $async.Future<$0.PairingResponse> pairingRequest(
      $grpc.ServiceCall call, $0.PairingRequest request);
}

@$pb.GrpcServiceName('kardash.project.proto.transport.CancelService')
class CancelServiceClient extends $grpc.Client {
  /// The hostname for this service.
  static const $core.String defaultHost = '';

  /// OAuth scopes needed for the client.
  static const $core.List<$core.String> oauthScopes = [
    '',
  ];

  CancelServiceClient(super.channel, {super.options, super.interceptors});

  $grpc.ResponseFuture<$0.CancelResponse> cancel(
    $0.CancelRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$cancel, request, options: options);
  }

  // method descriptors

  static final _$cancel =
      $grpc.ClientMethod<$0.CancelRequest, $0.CancelResponse>(
          '/kardash.project.proto.transport.CancelService/cancel',
          ($0.CancelRequest value) => value.writeToBuffer(),
          $0.CancelResponse.fromBuffer);
}

@$pb.GrpcServiceName('kardash.project.proto.transport.CancelService')
abstract class CancelServiceBase extends $grpc.Service {
  $core.String get $name => 'kardash.project.proto.transport.CancelService';

  CancelServiceBase() {
    $addMethod($grpc.ServiceMethod<$0.CancelRequest, $0.CancelResponse>(
        'cancel',
        cancel_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.CancelRequest.fromBuffer(value),
        ($0.CancelResponse value) => value.writeToBuffer()));
  }

  $async.Future<$0.CancelResponse> cancel_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.CancelRequest> $request) async {
    return cancel($call, await $request);
  }

  $async.Future<$0.CancelResponse> cancel(
      $grpc.ServiceCall call, $0.CancelRequest request);
}
