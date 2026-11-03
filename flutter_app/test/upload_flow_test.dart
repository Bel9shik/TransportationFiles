import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:http2/transport.dart' as h2;
import 'package:transportfiles/transfer/upload_flow.dart';

Future<void> settle() async {
  for (var i = 0; i < 10; i++) {
    await Future<void>.delayed(Duration.zero);
  }
}

void main() {
  test(
    'stalled transport bounds requests and resumes in order before EOF',
    () async {
      final transport = StreamController<h2.StreamMessage>();
      final received = <int>[];
      final finished = Completer<void>();
      late StreamSubscription<h2.StreamMessage> reader;
      reader = transport.stream.listen((message) {
        received.add((message as h2.DataStreamMessage).bytes.single);
        if (received.length == 3) reader.pause();
      }, onDone: finished.complete)..pause();
      final flow = UploadFlow();
      final sink = UploadSink(transport.sink, flow);
      var produced = 0;
      final producing = () async {
        for (var i = 0; i < 40; i++) {
          if (!await flow.reserve()) return;
          produced++;
          sink.add(h2.DataStreamMessage([i]));
        }
        await sink.close();
      }();

      await settle();
      expect(produced, UploadFlow.capacity);
      expect(received, isEmpty);
      reader.resume();
      await settle();
      expect(received, [0, 1, 2]);
      expect(produced, UploadFlow.capacity + 3);
      expect(finished.isCompleted, isFalse);
      reader.resume();
      await producing;
      await finished.future;
      expect(received, List.generate(40, (i) => i));
      expect(await flow.reserve(), isFalse);
    },
  );

  for (final cancelTransport in [false, true]) {
    test(
      'pending producer cleans up on ${cancelTransport ? 'reset' : 'abort'}',
      () async {
        final transport = StreamController<h2.StreamMessage>();
        final reader = transport.stream.listen((_) {})..pause();
        final flow = UploadFlow();
        final sink = UploadSink(transport.sink, flow);
        var cleaned = false;
        var produced = 0;
        final producing = () async {
          try {
            while (await flow.reserve()) {
              produced++;
              sink.add(h2.DataStreamMessage([produced]));
            }
          } finally {
            cleaned = true;
          }
        }();
        await settle();
        expect(produced, UploadFlow.capacity);
        if (cancelTransport) {
          await reader.cancel();
        } else {
          sink.abort();
        }
        await producing;
        expect(cleaned, isTrue);
        expect(produced, UploadFlow.capacity);
        // grpc-dart may still deliver a queued message while cancellation runs.
        sink.abort();
        sink.add(h2.DataStreamMessage([255]));
        if (!cancelTransport) await reader.cancel();
        await sink.done;
      },
    );
  }

  test('early sink error wakes producer and remains observable', () async {
    final flow = UploadFlow();
    final sink = UploadSink(_FailingSink(), flow);
    await expectLater(sink.done, throwsA(isA<StateError>()));
    expect(await flow.reserve(), isFalse);
    sink.abort();
  });
}

class _FailingSink implements StreamSink<h2.StreamMessage> {
  @override
  Future<void> addStream(Stream<h2.StreamMessage> stream) async {
    throw StateError('Transport failed before subscribing');
  }

  @override
  void add(h2.StreamMessage event) => throw UnimplementedError();
  @override
  void addError(Object error, [StackTrace? stackTrace]) =>
      throw UnimplementedError();
  @override
  Future<void> close() async {}
  @override
  Future<void> get done => Future.value();
}
