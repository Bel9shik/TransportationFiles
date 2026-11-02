import 'dart:async';
import 'dart:io';
import 'dart:typed_data';

// http2 emits one WINDOW_UPDATE for every received DATA fragment. Sending tiny
// credits immediately fragments the next DATA again, amplifying small writes.
// Only combine complete WINDOW_UPDATE frames produced by its FrameWriter; all
// other bytes pass unchanged. The timer also releases credit on idle streams.
Stream<List<int>> coalesceWindowUpdates(Stream<List<int>> input) {
  late StreamController<List<int>> output;
  late StreamSubscription<List<int>> subscription;
  final pending = <int, int>{};
  Timer? timer;
  void emit(int stream, int credit) {
    final frame = ByteData(13)
      ..setUint8(2, 4)
      ..setUint8(3, 8)
      ..setUint32(5, stream)
      ..setUint32(9, credit);
    output.add(frame.buffer.asUint8List());
  }

  void flush() {
    timer?.cancel();
    timer = null;
    if (pending.isEmpty) return;
    final updates = Map<int, int>.of(pending);
    pending.clear();
    updates.forEach(emit);
  }

  output = StreamController<List<int>>(
    sync: true,
    onListen: () {
      subscription = input.listen(
        (bytes) {
          if (bytes.length == 13 &&
              bytes[0] == 0 &&
              bytes[1] == 0 &&
              bytes[2] == 4 &&
              bytes[3] == 8 &&
              bytes[4] == 0) {
            final data = ByteData.sublistView(Uint8List.fromList(bytes));
            final stream = data.getUint32(5);
            final credit = data.getUint32(9);
            if (stream <= 0x7fffffff && credit > 0 && credit <= 0x7fffffff) {
              var previous = pending[stream] ?? 0;
              if (previous + credit > 0x7fffffff) {
                emit(stream, previous);
                previous = 0;
              }
              final combined = previous + credit;
              if (combined >= 16 * 1024) {
                pending.remove(stream);
                emit(stream, combined);
              } else {
                pending[stream] = combined;
                // Batch the current event turn, without imposing the Windows
                // timer granularity on a peer waiting for the remaining credit.
                timer ??= Timer(Duration.zero, flush);
              }
              return;
            }
          }
          // Flush before RST_STREAM/GOAWAY/END_STREAM so no delayed credit is
          // emitted after the stream or connection has been closed.
          flush();
          output.add(bytes);
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
      pending.clear();
      return subscription.cancel();
    },
  );
  return output.stream;
}

// FrameWriter emits each DATA frame separately, even when they belong
// to one gRPC block. Batch synchronous writes into a single socket write while
// retaining the byte order and propagating socket backpressure.
Stream<List<int>> coalesceSocketWrites(Stream<List<int>> input) {
  late StreamController<List<int>> output;
  late StreamSubscription<List<int>> subscription;
  final bytes = BytesBuilder(copy: false);
  var scheduled = false, cancelled = false;
  void flush() {
    scheduled = false;
    if (cancelled || output.isClosed || bytes.isEmpty) return;
    output.add(bytes.takeBytes());
  }

  output = StreamController<List<int>>(
    sync: true,
    onListen: () {
      subscription = input.listen(
        (data) {
          bytes.add(data);
          if (bytes.length >= 1024 * 1024) {
            flush();
          } else if (!scheduled) {
            scheduled = true;
            Timer.run(flush);
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
      cancelled = true;
      bytes.clear();
      return subscription.cancel();
    },
  );
  return output.stream;
}

// http2's BufferedSink uses addStream; retaining Socket.addStream preserves
// backpressure all the way to its HTTP/2 queues instead of buffering the file.
class WindowUpdateSocketSink implements StreamSink<List<int>> {
  final Socket socket;
  WindowUpdateSocketSink(this.socket);
  @override
  void add(List<int> data) => socket.add(data);
  @override
  void addError(Object error, [StackTrace? stackTrace]) =>
      socket.addError(error, stackTrace);
  @override
  Future<void> addStream(Stream<List<int>> stream) =>
      socket.addStream(coalesceSocketWrites(coalesceWindowUpdates(stream)));
  @override
  Future<void> close() async {
    await socket.close();
  }

  @override
  Future<void> get done async {
    await socket.done;
  }
}
