import 'dart:async';
import 'dart:io';

import 'package:path/path.dart' as p;
import 'package:transportfiles/transfer/files.dart';
import 'package:transportfiles/transfer/transfer.dart';

// Compile with dart compile exe: dart run would measure JIT instead of AOT.
Future<void> main(List<String> args) async {
  if (args[0] == 'serve') {
    final root = Directory(args[1]);
    final node = TransferNode(
      approve: (_, _) async => p.join(root.path, randomId()),
      progress: (_, _) {},
      received: (_) {},
      failed: stderr.writeln,
    );
    try {
      await node.start();
      await File(p.join(root.path, 'port')).writeAsString('${node.port}');
      await stdin.first;
    } finally {
      await node.close();
    }
  } else if (args[0] == 'send') {
    final sender = TransferSender(Endpoint('127.0.0.1', int.parse(args[1])));
    final runs = int.parse(args[2]);
    final files = args
        .skip(3)
        .map((s) => SourceFile(File(s), p.basename(s)))
        .toList();
    try {
      for (var i = 0; i < runs; i++) {
        final timer = Stopwatch()..start();
        await sender.send(files, 'Benchmark', () {}, (_, _) {});
        timer.stop();
        stdout.writeln(timer.elapsedMicroseconds / 1e6);
      }
    } finally {
      await sender.close();
    }
  } else {
    throw ArgumentError('Expected serve or send');
  }
}
