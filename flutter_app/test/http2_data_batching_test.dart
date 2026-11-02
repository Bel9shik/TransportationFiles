import 'dart:async';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:http2/transport.dart' as h2;
import 'package:transportfiles/transfer/http2_data_batching.dart';

void main() {
  test('preserves bytes, header ordering and END_STREAM', () async {
    final headers = h2.HeadersStreamMessage([h2.Header.ascii('key', 'value')]);
    final result = await coalesceDataMessages(
      Stream.fromIterable([
        h2.DataStreamMessage([1, 2]),
        h2.DataStreamMessage([3]),
        headers,
        h2.DataStreamMessage([4]),
        h2.DataStreamMessage([], endStream: true),
      ]),
    ).toList();
    expect(result.length, 3);
    expect((result[0] as h2.DataStreamMessage).bytes, [1, 2, 3]);
    expect(result[0].endStream, false);
    expect(result[1], same(headers));
    expect((result[2] as h2.DataStreamMessage).bytes, [4]);
    expect(result[2].endStream, true);
  });
  test('flushes unary messages without waiting for stream closure', () async {
    final source = StreamController<h2.StreamMessage>();
    final result = coalesceDataMessages(source.stream).first;
    source.add(h2.DataStreamMessage([1]));
    expect(
      (await result.timeout(
        const Duration(seconds: 1),
      ) as h2.DataStreamMessage).bytes,
      [1],
    );
    await source.close();
  });
  test('batches a burst with bounded aggregate size', () async {
    final result = await coalesceDataMessages(
      Stream.fromIterable(
        List.generate(40, (_) => h2.DataStreamMessage(Uint8List(64 * 1024))),
      ),
    ).toList();
    expect(result.length, 3);
    expect(result.map((m) => (m as h2.DataStreamMessage).bytes.length), [
      1024 * 1024,
      1024 * 1024,
      512 * 1024,
    ]);
  });
  test('sink closes after forwarding pending bytes', () async {
    final target = StreamController<h2.StreamMessage>();
    final seen = <Object>[];
    final completed = target.stream
        .listen(seen.add, onError: seen.add)
        .asFuture<void>();
    final sink = BatchedDataSink(target.sink);
    sink.add(h2.DataStreamMessage([1]));
    await sink.close();
    await completed;
    expect((seen.single as h2.DataStreamMessage).bytes, [1]);
  });
  test('error retains ordering and does not discard later data', () async {
    final source = StreamController<h2.StreamMessage>(sync: true);
    final events = <Object>[];
    final completed = Completer<void>();
    coalesceDataMessages(source.stream).listen(
      events.add,
      onError: (Object error) => events.add(error),
      onDone: completed.complete,
    );
    final error = StateError('probe');
    source.add(h2.DataStreamMessage([1]));
    source.addError(error);
    source.add(h2.DataStreamMessage([2], endStream: true));
    await source.close();
    await completed.future;
    expect((events[0] as h2.DataStreamMessage).bytes, [1]);
    expect(events[1], same(error));
    expect((events[2] as h2.DataStreamMessage).bytes, [2]);
    expect((events[2] as h2.DataStreamMessage).endStream, true);
  });
  test('cancel discards pending data and cancels upstream', () async {
    var cancelled = false;
    final source = StreamController<h2.StreamMessage>(
      sync: true,
      onCancel: () {
        cancelled = true;
      },
    );
    final events = <h2.StreamMessage>[];
    final subscription = coalesceDataMessages(source.stream).listen(events.add);
    source.add(h2.DataStreamMessage([1]));
    await subscription.cancel();
    await Future<void>.delayed(Duration.zero);
    expect(cancelled, true);
    expect(events, isEmpty);
    await source.close();
  });
}
