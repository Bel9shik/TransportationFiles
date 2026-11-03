import 'dart:async';
import 'dart:io';
import 'dart:typed_data';

import 'package:crypto/crypto.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:path/path.dart' as p;
import 'package:transportfiles/transfer/prepared_file_source.dart';

const blockSize = 1024 * 1024;

void main() {
  late Directory root;
  late PreparedFileSource source;

  setUp(() async {
    root = await Directory.systemTemp.createTemp('transport-reader-');
    source = PreparedFileSource();
  });
  tearDown(() async {
    await source.close();
    await root.delete(recursive: true);
  });

  for (final size in [0, 3, 4 * blockSize + 7]) {
    test('worker reads and hashes $size bytes in one pass', () async {
      final bytes = Uint8List.fromList(List.generate(size, (i) => i % 251));
      final file = await File(p.join(root.path, 'data')).writeAsBytes(bytes);
      final parts = await source.readFile(file, size).toList();
      expect(parts.last.isDigest, isTrue);
      expect(parts.last.bytes, sha256.convert(bytes).bytes);
      final data = BytesBuilder(copy: false);
      for (final part in parts.take(parts.length - 1)) {
        expect(part.isDigest, isFalse);
        expect(part.bytes.length, lessThanOrEqualTo(blockSize));
        data.add(part.bytes);
      }
      expect(data.takeBytes(), bytes);
      await file.delete(); // Windows requires the worker's input to be closed.
    });
  }

  test('paused consumer permits only one prefetched block', () async {
    final bytes = Uint8List(4 * blockSize);
    final file = await File(p.join(root.path, 'data')).writeAsBytes(bytes);
    final reader = StreamIterator(source.readFile(file, bytes.length));
    try {
      expect(await reader.moveNext(), isTrue);
      final data = BytesBuilder(copy: false)..add(reader.current.bytes);
      // The worker may prepare block two while the consumer holds block one.
      // It must not read block three or four until the consumer asks for more.
      await Future<void>.delayed(const Duration(milliseconds: 30));
      final change = Uint8List(blockSize)..fillRange(0, blockSize, 17);
      final writer = await file.open(mode: FileMode.append);
      try {
        await writer.setPosition(2 * blockSize);
        await writer.writeFrom(change);
      } finally {
        await writer.close();
      }
      List<int>? digest;
      while (await reader.moveNext()) {
        final part = reader.current;
        if (part.isDigest) {
          digest = part.bytes;
        } else {
          data.add(part.bytes);
        }
      }
      final current = await file.readAsBytes();
      expect(current.length, bytes.length);
      expect(data.takeBytes(), current);
      expect(digest, sha256.convert(current).bytes);
    } finally {
      await reader.cancel();
    }
  });

  test(
    'missing and changed files preserve errors and worker stays reusable',
    () async {
      final file = File(p.join(root.path, 'data'));
      await expectLater(
        source.readFile(file, 1).toList(),
        throwsA(isA<FileSystemException>()),
      );
      await file.writeAsBytes([1, 2, 3]);
      await expectLater(
        source.readFile(file, 4).toList(),
        throwsA(isA<FormatException>()),
      );
      final parts = await source.readFile(file, 3).toList();
      expect(parts.first.bytes, [1, 2, 3]);
      expect(parts.last.bytes, sha256.convert([1, 2, 3]).bytes);
    },
  );

  for (final grow in [false, true]) {
    test(
      '${grow ? 'growing' : 'truncated'} input closes on size error',
      () async {
        final file = await File(p.join(root.path, 'data'))
            .writeAsBytes(Uint8List(4 * blockSize));
        final reader = StreamIterator(source.readFile(file, 4 * blockSize));
        try {
          expect(await reader.moveNext(), isTrue);
          final writer = await file.open(mode: FileMode.append);
          try {
            await writer.truncate(grow ? 5 * blockSize : 2 * blockSize);
          } finally {
            await writer.close();
          }
          await expectLater(() async {
            while (await reader.moveNext()) {}
          }(), throwsA(isA<FormatException>()));
        } finally {
          await reader.cancel();
        }
        await file.delete();
      },
    );
  }

  test('cancel drains pending read, closes input and reuses worker', () async {
    final file = await File(p.join(root.path, 'data'))
        .writeAsBytes(Uint8List(4 * blockSize));
    await source.readFile(file, 4 * blockSize).take(1).drain<void>();
    await file.delete();
    final next = await File(p.join(root.path, 'next')).writeAsBytes([42]);
    final parts = await source.readFile(next, 1).toList();
    expect(parts.first.bytes, [42]);
    expect(parts.last.bytes, sha256.convert([42]).bytes);
  });

  test('close during a pending first moveNext terminates cleanly', () async {
    final file = await File(p.join(root.path, 'data'))
        .writeAsBytes(Uint8List(4 * blockSize));
    final warmup = await File(p.join(root.path, 'warmup')).writeAsBytes([42]);
    // Exercise both startup and reused-worker open/read races. The worker's
    // reply and exit notifications travel on separate ports and may race.
    for (var attempt = 0; attempt < 20; attempt++) {
      final worker = PreparedFileSource();
      StreamIterator<PreparedFilePart>? reader;
      try {
        if (attempt.isOdd) await worker.readFile(warmup, 1).drain<void>();
        reader = StreamIterator(worker.readFile(file, 4 * blockSize));
        final pending = reader.moveNext();
        // Let the generator enter its asynchronous open/read before closing.
        await Future<void>.delayed(Duration.zero);
        final closing = worker.close();
        await pending;
        await closing;
        expect(await reader.moveNext(), isFalse);
      } finally {
        await reader?.cancel();
        await worker.close();
      }
    }
    await file.delete();
  });

  test(
    'close awaits pending read and worker exit even with a paused consumer',
    () async {
      final file = await File(p.join(root.path, 'data'))
          .writeAsBytes(Uint8List(4 * blockSize));
      final reader = StreamIterator(source.readFile(file, 4 * blockSize));
      expect(await reader.moveNext(), isTrue);
      await Future.wait([source.close(), source.close()]);
      await reader.cancel();
      await file.delete();
      await expectLater(
        source.readFile(file, 0).toList(),
        throwsA(isA<StateError>()),
      );
    },
  );
}
