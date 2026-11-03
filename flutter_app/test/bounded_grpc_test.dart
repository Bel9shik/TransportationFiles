import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:grpc/grpc.dart';
import 'package:http2/transport.dart';
import 'package:transportfiles/transfer/bounded_grpc.dart';

void main() {
  List<int> header(int size, [int compression = 0]) =>
      (ByteData(5)
            ..setUint8(0, compression)
            ..setUint32(1, size))
          .buffer
          .asUint8List();
  test(
    'fragmented oversized envelope is rejected before forwarding its length',
    () async {
      final bytes = header(0xffffffff);
      final seen = <StreamMessage>[];
      await expectLater(
        boundedMessages(
          Stream.fromIterable(bytes.map((b) => DataStreamMessage([b]))),
        ).forEach(seen.add),
        throwsA(isA<GrpcError>()),
      );
      expect(seen.length, 4);
    },
  );
  test('compressed envelopes are not accepted', () async {
    await expectLater(
      boundedMessages(Stream.value(DataStreamMessage(header(3, 1))))
          .drain<void>(),
      throwsA(isA<GrpcError>()),
    );
  });
  test(
    'multiple frames, split headers and empty frames remain intact',
    () async {
      final bytes = [...header(3), 1, 2, 3, ...header(0), ...header(2), 4, 5];
      final output = await boundedMessages(
        Stream.fromIterable(bytes.map((b) => DataStreamMessage([b]))),
      ).toList();
      expect(output.cast<DataStreamMessage>().expand((m) => m.bytes), bytes);
    },
  );
  test('truncated envelopes fail', () async {
    await expectLater(
      boundedMessages(Stream.value(DataStreamMessage([...header(2), 1])))
          .drain<void>(),
      throwsA(isA<GrpcError>()),
    );
  });
}
