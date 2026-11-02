import 'dart:typed_data';

import 'package:crypto/crypto.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:transportfiles/transfer/streaming_digest.dart';

void main() {
  for (final size in [0, 1, 55, 56, 63, 64, 65, 65536, 1048577]) {
    test(
      'native streaming SHA-256 matches reference for $size bytes',
      () async {
        final data = Uint8List.fromList(List.generate(size, (i) => i % 251));
        final hash = StreamingDigest();
        try {
          for (var offset = 0; offset < size; offset += 32767) {
            final end = (offset + 32767).clamp(0, size);
            await hash.add(Uint8List.sublistView(data, offset, end));
          }
          expect(await hash.finish(), sha256.convert(data).bytes);
        } finally {
          await hash.close();
        }
      },
    );
  }
  test('large native input and independent digest handles', () async {
    final data = Uint8List.fromList(
      List.generate(3 * 1024 * 1024 + 7, (i) => i % 251),
    );
    final a = StreamingDigest(), b = StreamingDigest();
    try {
      await a.add(data);
      await b.add([1, 2, 3]);
      expect(await b.finish(), sha256.convert([1, 2, 3]).bytes);
      final result = await a.finish();
      expect(result, sha256.convert(data).bytes);
      await a.close();
      expect(await a.finish(), result);
    } finally {
      await a.close();
      await b.close();
    }
  });
  test(
    'closing an unused digest is idempotent and rejects new input',
    () async {
      final hash = StreamingDigest();
      await hash.close();
      await hash.close();
      await expectLater(hash.add([1]), throwsStateError);
    },
  );
}
