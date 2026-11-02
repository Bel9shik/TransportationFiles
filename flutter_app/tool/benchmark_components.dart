import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:transportfiles/generated/transport_files.pb.dart';
import 'package:transportfiles/transfer/streaming_digest.dart';

// Diagnostic timings only; the end-to-end benchmark independently verifies
// SHA-256 and file counts. Keep forced disk flush separate from write/close.
Future<void> benchmarkComponents() async {
  final block = Uint8List.fromList(List.generate(1024 * 1024, (i) => i % 251));
  final directory = await Directory.systemTemp.createTemp('transport-parts-');
  final file = File('${directory.path}/data.bin');
  final samples = <Map<String, double>>[];
  Future<double> timed(Future<void> Function() work) async {
    final watch = Stopwatch()..start();
    await work();
    return watch.elapsedMicroseconds / 1e6;
  }

  try {
    for (var run = 0; run < 4; run++) {
      final result = <String, double>{};
      result['sha256_100MiB'] = await timed(() async {
        final hash = StreamingDigest();
        try {
          for (var i = 0; i < 100; i++) {
            await hash.add(block);
          }
          if ((await hash.finish()).length != 32) throw StateError('Digest');
        } finally {
          await hash.close();
        }
      });
      final output = await file.open(mode: FileMode.write);
      try {
        result['write_100MiB'] = await timed(() async {
          for (var i = 0; i < 100; i++) {
            await output.writeFrom(block);
          }
        });
        result['force_disk_flush'] = await timed(output.flush);
      } finally {
        await output.close();
      }
      result['read_100MiB'] = await timed(() async {
        final input = await file.open();
        var count = 0;
        try {
          while (true) {
            final data = await input.read(1024 * 1024);
            if (data.isEmpty) break;
            count += data.length;
          }
        } finally {
          await input.close();
        }
        if (count != 100 * block.length) throw StateError('File length');
      });
      result['protobuf_100MiB'] = await timed(() async {
        final chunk = FileChunk(
          filename: 'large.bin',
          transferToken: 'benchmark-token',
          content: Uint8List.sublistView(block, 0, 64 * 1024),
        );
        var count = 0;
        for (var i = 0; i < 1600; i++) {
          count += FileChunk.fromBuffer(chunk.writeToBuffer()).content.length;
        }
        if (count != 100 * block.length) throw StateError('Protobuf length');
      });
      result['tcp_100MiB'] = await timed(() async {
        final listener = await ServerSocket.bind(
          InternetAddress.loopbackIPv4,
          0,
        );
        final received = listener.first.then((socket) async {
          socket.setOption(SocketOption.tcpNoDelay, true);
          try {
            return await socket.fold<int>(
              0,
              (count, bytes) => count + bytes.length,
            );
          } finally {
            socket.destroy();
          }
        });
        final sender = await Socket.connect(
          InternetAddress.loopbackIPv4,
          listener.port,
        );
        try {
          sender.setOption(SocketOption.tcpNoDelay, true);
          await sender.addStream(Stream.fromIterable(List.filled(100, block)));
          await sender.close();
          if (await received != 100 * block.length) {
            throw StateError('TCP length');
          }
        } finally {
          sender.destroy();
          await listener.close();
        }
      });
      if (run > 0) samples.add(result);
    }
    stdout.writeln(jsonEncode({'component_seconds': samples}));
  } finally {
    await directory.delete(recursive: true);
  }
}
