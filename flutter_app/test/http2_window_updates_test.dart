import 'dart:async';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:transportfiles/transfer/http2_window_updates.dart';

Uint8List update(int stream, int credit) =>
    (ByteData(13)
          ..setUint8(2, 4)
          ..setUint8(3, 8)
          ..setUint32(5, stream)
          ..setUint32(9, credit))
        .buffer
        .asUint8List();

int credit(List<int> bytes) =>
    ByteData.sublistView(Uint8List.fromList(bytes)).getUint32(9);
int streamId(List<int> bytes) =>
    ByteData.sublistView(Uint8List.fromList(bytes)).getUint32(5);

void main() {
  test(
    'synchronous HTTP/2 writes are batched without reordering bytes',
    () async {
      final source = StreamController<List<int>>(sync: true);
      final result = coalesceSocketWrites(source.stream).toList();
      source.add([1, 2]);
      source.add([3]);
      source.add([4, 5]);
      await source.close();
      expect(await result, [
        [1, 2, 3, 4, 5],
      ]);
    },
  );

  test(
    'socket batching flushes at the byte bound and propagates pause/cancel',
    () async {
      var paused = false, cancelled = false;
      final source = StreamController<List<int>>(
        sync: true,
        onPause: () => paused = true,
        onResume: () => paused = false,
        onCancel: () => cancelled = true,
      );
      final sizes = <int>[];
      final subscription = coalesceSocketWrites(source.stream)
          .listen((b) => sizes.add(b.length));
      for (var i = 0; i < 4; i++) {
        source.add(Uint8List(16 * 1024));
      }
      expect(sizes, [64 * 1024]);
      subscription.pause();
      expect(paused, isTrue);
      subscription.resume();
      source.add([1]);
      await subscription.cancel();
      await Future<void>.delayed(Duration.zero);
      expect(cancelled, isTrue);
      expect(sizes, [64 * 1024]);
      await source.close();
    },
  );

  test('large updates cannot overflow the HTTP/2 31-bit increment', () async {
    final result = await coalesceWindowUpdates(
      Stream.fromIterable([update(0, 71), update(0, 0x7fffffff)]),
    ).toList();
    expect(result, [update(0, 71), update(0, 0x7fffffff)]);
  });

  test('downstream pause and cancellation reach the source', () async {
    var paused = false, cancelled = false;
    final source = StreamController<List<int>>(
      sync: true,
      onPause: () => paused = true,
      onResume: () => paused = false,
      onCancel: () => cancelled = true,
    );
    final subscription = coalesceWindowUpdates(source.stream).listen((_) {});
    source.add(update(0, 71));
    subscription.pause();
    expect(paused, isTrue);
    subscription.resume();
    await subscription.cancel();
    expect(cancelled, isTrue);
    await source.close();
  });

  test('errors propagate after pending credit', () async {
    final error = StateError('socket failed');
    final source = StreamController<List<int>>();
    final events = <Object>[];
    final done = coalesceWindowUpdates(source.stream)
        .map<Object>((frame) => credit(frame))
        .handleError((Object e) => events.add(e))
        .forEach(events.add);
    source.add(update(0, 71));
    source.addError(error);
    await source.close();
    await done;
    expect(events, [71, same(error)]);
  });

  test('tiny HTTP/2 credits are combined without losing connection or stream credit', () async {
    final result = await coalesceWindowUpdates(
      Stream.fromIterable([
        for (var i = 0; i < 1000; i++) ...[update(0, 71), update(1, 71)],
      ]),
    ).toList();
    for (final id in [0, 1]) {
      final frames = result.where((f) => streamId(f) == id);
      expect(frames.map(credit).reduce((a, b) => a + b), 71000);
      expect(frames.length, lessThan(10));
    }
  });

  test('small credit is flushed while the connection stays open', () async {
    final source = StreamController<List<int>>();
    final first = coalesceWindowUpdates(source.stream).first;
    source.add(update(0, 71));
    expect(await first.timeout(const Duration(seconds: 1)), update(0, 71));
    await source.close();
  });

  test(
    'pending credit precedes control data and other bytes stay unchanged',
    () async {
      final control = Uint8List.fromList([0, 0, 0, 1, 1, 0, 0, 0, 1]);
      final result = await coalesceWindowUpdates(
        Stream.fromIterable([update(1, 71), update(1, 71), control]),
      ).toList();
      expect(result, [update(1, 142), control]);
    },
  );
}
