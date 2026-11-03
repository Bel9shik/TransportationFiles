import 'dart:async';
import 'dart:io';

import 'package:path/path.dart' as p;
import 'package:transportfiles/transfer/files.dart';
import 'package:transportfiles/transfer/transfer.dart';

Future<void> main(List<String> args) async {
  final exchange = Directory(args.single);
  final root = await Directory.systemTemp.createTemp('dart-java-interop-');
  final incoming = <List<File>>[];
  final received = Completer<void>();
  final node = TransferNode(
    approve: (_, _) async => p.join(root.path, randomId()),
    progress: (_, _) {},
    received: (files) {
      incoming.add(files);
      if (incoming.length == 2) received.complete();
    },
    failed: (error) {
      if (!received.isCompleted) received.completeError(StateError(error));
    },
  );
  try {
    await node.start();
    await File(p.join(exchange.path, 'dart-port'))
        .writeAsString('${node.port}');
    await received.future.timeout(const Duration(seconds: 90));
    final expected = List.generate(1048577, (i) => i % 251);
    for (final file in [incoming[0].single, incoming[1].first]) {
      final actual = await file.readAsBytes();
      if (actual.length != expected.length ||
          !List.generate(
            actual.length,
            (i) => actual[i] == expected[i],
          ).every((e) => e)) {
        throw StateError('Bytes differ');
      }
    }
    if (await incoming[1][1].length() != 0) {
      throw StateError('Empty file differs');
    }
    final port = int.parse(
      await File(p.join(exchange.path, 'java-port')).readAsString(),
    );
    final sender = TransferSender(Endpoint('127.0.0.1', port));
    try {
      await sender.send(
        [SourceFile(incoming[0].single, 'Фото 🌆.bin')],
        'Dart',
        () {},
        (_, _) {},
      );
      await sender.send(
        [
          SourceFile(incoming[1][0], 'Фото.bin'),
          SourceFile(incoming[1][1], 'Фото.bin'),
        ],
        'Dart',
        () {},
        (_, _) {},
      );
    } finally {
      await sender.close();
    }
    stdout.writeln('PASS Dart <-> Java: bytes and names verified');
  } finally {
    await node.close();
    await root.delete(recursive: true);
  }
}
