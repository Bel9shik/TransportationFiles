import 'dart:async';
import 'dart:io';
import 'dart:math';
import 'dart:typed_data';

import 'package:grpc/grpc.dart';
import 'package:http2/transport.dart' as h2;

// grpc-dart 5.2 allocates the declared message length before deserializing it.
// Validate the five-byte envelope before forwarding it to that decoder, on
// both client and server. Our protocol never uses compressed gRPC messages.
Stream<h2.StreamMessage> boundedMessages(
  Stream<h2.StreamMessage> input,
) async* {
  final header = Uint8List(5);
  var headerCount = 0, remaining = 0;
  await for (final message in input) {
    if (message is h2.DataStreamMessage) {
      var offset = 0;
      while (offset < message.bytes.length) {
        if (remaining > 0) {
          final count = min(remaining, message.bytes.length - offset);
          remaining -= count;
          offset += count;
        } else {
          header[headerCount++] = message.bytes[offset++];
          if (headerCount == 5) {
            remaining = header.buffer.asByteData().getUint32(1);
            if (header[0] != 0 || remaining > 128 * 1024) {
              throw const GrpcError.resourceExhausted(
                'Недопустимый размер или сжатие сетевого сообщения',
              );
            }
            headerCount = 0;
          }
        }
      }
    }
    yield message;
  }
  if (headerCount != 0 || remaining != 0) {
    throw const GrpcError.dataLoss('Сетевое сообщение получено не полностью');
  }
}

abstract class _Connection implements h2.TransportConnection {
  final h2.TransportConnection inner;
  _Connection(this.inner);
  @override
  Future ping() => inner.ping();
  @override
  set onActiveStateChanged(void Function(bool) value) {
    inner.onActiveStateChanged = value;
  }

  @override
  Future<void> get onInitialPeerSettingsReceived =>
      inner.onInitialPeerSettingsReceived;
  @override
  Stream<int> get onPingReceived => inner.onPingReceived;
  @override
  Stream<void> get onFrameReceived => inner.onFrameReceived;
  @override
  Future finish() => inner.finish();
  @override
  Future terminate([int? errorCode]) => inner.terminate(errorCode);
}

class _ServerConnection extends _Connection
    implements h2.ServerTransportConnection {
  _ServerConnection(h2.ServerTransportConnection super.inner);
  @override
  Stream<h2.ServerTransportStream> get incomingStreams =>
      (inner as h2.ServerTransportConnection).incomingStreams.map(
        _ServerStream.new,
      );
}

class _ClientConnection extends _Connection
    implements h2.ClientTransportConnection {
  _ClientConnection(h2.ClientTransportConnection super.inner);
  @override
  bool get isOpen => (inner as h2.ClientTransportConnection).isOpen;
  @override
  h2.ClientTransportStream makeRequest(
    List<h2.Header> headers, {
    bool endStream = false,
  }) => _ClientStream(
    (inner as h2.ClientTransportConnection).makeRequest(
      headers,
      endStream: endStream,
    ),
  );
}

abstract class _Stream extends h2.TransportStream {
  final h2.TransportStream inner;
  _Stream(this.inner);
  @override
  int get id => inner.id;
  @override
  Stream<h2.StreamMessage> get incomingMessages =>
      boundedMessages(inner.incomingMessages);
  @override
  StreamSink<h2.StreamMessage> get outgoingMessages => inner.outgoingMessages;
  @override
  set onTerminated(void Function(int?) value) {
    inner.onTerminated = value;
  }

  @override
  void terminate() => inner.terminate();
}

class _ServerStream extends _Stream implements h2.ServerTransportStream {
  _ServerStream(h2.ServerTransportStream super.inner);
  @override
  bool get canPush => false;
  @override
  h2.ServerTransportStream push(List<h2.Header> headers) =>
      throw UnsupportedError('Server push is not used');
}

class _ClientStream extends _Stream implements h2.ClientTransportStream {
  _ClientStream(h2.ClientTransportStream super.inner);
  @override
  Stream<h2.TransportStreamPush> get peerPushes =>
      (inner as h2.ClientTransportStream).peerPushes;
}

class BoundedServer extends Server {
  BoundedServer({required super.services}) : super.create();
  @override
  Future<void> serveConnection({
    required h2.ServerTransportConnection connection,
    X509Certificate? clientCertificate,
    InternetAddress? remoteAddress,
  }) => super.serveConnection(
    connection: _ServerConnection(connection),
    clientCertificate: clientCertificate,
    remoteAddress: remoteAddress,
  );
}

class BoundedConnector implements ClientTransportConnector {
  final String host;
  final int port;
  Socket? _socket;
  var _generation = 0;
  BoundedConnector(this.host, this.port);
  @override
  Future<h2.ClientTransportConnection> connect() async {
    final generation = ++_generation;
    final socket = await Socket.connect(
      host,
      port,
      timeout: const Duration(seconds: 10),
    );
    if (generation != _generation) {
      socket.destroy();
      throw const GrpcError.cancelled();
    }
    _socket = socket;
    socket.setOption(SocketOption.tcpNoDelay, true);
    return _ClientConnection(h2.ClientTransportConnection.viaSocket(socket));
  }

  @override
  Future get done => _socket!.done;
  @override
  void shutdown() {
    _generation++;
    _socket?.destroy();
  }

  @override
  String get authority => '${host.contains(':') ? '[$host]' : host}:$port';
}
