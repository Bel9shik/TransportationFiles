import 'dart:async';
import 'dart:typed_data';

import 'package:http2/transport.dart' as h2;

// Merge adjacent DATA messages before http2 schedules its connection queue.
// gRPC message envelopes remain intact inside the concatenated byte stream.
Stream<h2.StreamMessage> coalesceDataMessages(Stream<h2.StreamMessage> input) {
  late StreamController<h2.StreamMessage> output;
  late StreamSubscription<h2.StreamMessage> subscription;
  final bytes = BytesBuilder(copy: false);
  Timer? timer;
  void flush({bool endStream = false}) {
    timer?.cancel();
    timer = null;
    if (output.isClosed || (bytes.isEmpty && !endStream)) return;
    output.add(h2.DataStreamMessage(bytes.takeBytes(), endStream: endStream));
  }

  output = StreamController<h2.StreamMessage>(
    sync: true,
    onListen: () {
      subscription = input.listen(
        (message) {
          if (message is h2.DataStreamMessage) {
            bytes.add(message.bytes);
            if (message.endStream || bytes.length >= 1024 * 1024) {
              flush(endStream: message.endStream);
            } else {
              timer ??= Timer(Duration.zero, flush);
            }
          } else {
            flush();
            output.add(message);
          }
        },
        onError: (Object error, StackTrace stack) {
          flush();
          output.addError(error, stack);
        },
        onDone: () {
          flush();
          output.close();
        },
      );
    },
    onPause: () => subscription.pause(),
    onResume: () => subscription.resume(),
    onCancel: () {
      timer?.cancel();
      bytes.clear();
      return subscription.cancel();
    },
  );
  return output.stream;
}

class BatchedDataSink implements StreamSink<h2.StreamMessage> {
  final _input = StreamController<h2.StreamMessage>(sync: true);
  late final Future<void> _done;
  BatchedDataSink(StreamSink<h2.StreamMessage> inner) {
    _done = coalesceDataMessages(_input.stream).pipe(inner);
    // Observe a peer reset even if it precedes the caller closing its sink.
    unawaited(_done.then<void>((_) {}, onError: (Object _, StackTrace _) {}));
  }
  @override
  void add(h2.StreamMessage message) => _input.add(message);
  @override
  void addError(Object error, [StackTrace? stack]) =>
      _input.addError(error, stack);
  @override
  Future<void> addStream(Stream<h2.StreamMessage> stream) =>
      _input.addStream(stream);
  @override
  Future<void> close() async {
    await _input.close();
    await _done;
  }

  @override
  Future<void> get done => _done;
}
