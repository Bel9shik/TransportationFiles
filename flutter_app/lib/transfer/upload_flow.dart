import 'dart:async';

import 'package:http2/transport.dart' as h2;

// grpc-dart forwards request streams with listen(sink.add), which loses the
// HTTP/2 sink's pause signal. Keep only a small number of serialized requests
// ahead of the transport; every FileChunk, including the final hash, reserves
// a permit before entering grpc-dart's queues.
class UploadFlow {
  static const capacity = 16;
  var _available = capacity;
  var _aborted = false;
  Completer<bool>? _waiting;

  Future<bool> reserve() {
    if (_aborted) return Future.value(false);
    if (_available > 0) {
      _available--;
      return Future.value(true);
    }
    if (_waiting != null) {
      throw StateError('Only one upload producer may reserve a permit');
    }
    return (_waiting = Completer<bool>()).future;
  }

  void delivered() {
    if (_aborted) return;
    final waiting = _waiting;
    if (waiting != null) {
      _waiting = null;
      waiting.complete(true);
    } else if (_available < capacity) {
      _available++;
    }
  }

  void abort() {
    if (_aborted) return;
    _aborted = true;
    _waiting?.complete(false);
    _waiting = null;
  }
}

// addStream propagates HTTP/2 flow-control pauses to this controller. A permit
// is returned only when its DATA event leaves the controller for that sink.
// The request HEADERS have already been added by makeRequest before wrapping.
class UploadSink implements StreamSink<h2.StreamMessage> {
  final StreamSink<h2.StreamMessage> _inner;
  final UploadFlow _flow;
  final _controller = StreamController<h2.StreamMessage>();
  late final Future<void> _done;
  var _aborted = false;

  UploadSink(this._inner, this._flow) {
    _controller.onCancel = _flow.abort;
    _done = _drain();
    // Transport failures can arrive before grpc-dart calls close or observes
    // done. Keep that error observable through done without an unhandled error.
    _done.ignore();
  }

  Future<void> _drain() async {
    try {
      await _inner.addStream(
        _controller.stream.map((message) {
          if (message is h2.DataStreamMessage) _flow.delivered();
          return message;
        }),
      );
      await _inner.close();
    } finally {
      abort();
    }
  }

  void abort() {
    _aborted = true;
    _flow.abort();
    if (!_controller.isClosed) _controller.close();
  }

  @override
  void add(h2.StreamMessage event) {
    if (!_aborted) _controller.add(event);
  }

  @override
  void addError(Object error, [StackTrace? stackTrace]) {
    if (_aborted) return;
    _flow.abort();
    _controller.addError(error, stackTrace);
  }

  @override
  Future<void> addStream(Stream<h2.StreamMessage> stream) =>
      _controller.addStream(stream);

  @override
  Future<void> close() {
    if (!_controller.isClosed) _controller.close();
    return _done;
  }

  @override
  Future<void> get done => _done;
}
