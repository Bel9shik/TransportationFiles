import 'dart:async';
import 'dart:io';
import 'dart:convert';

import 'package:crypto/crypto.dart';
import 'package:fixnum/fixnum.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:grpc/grpc.dart';
import 'package:path/path.dart' as p;
import 'package:transportfiles/generated/transport_files.pbgrpc.dart' as wire;
import 'package:transportfiles/transfer/files.dart';
import 'package:transportfiles/transfer/discovery.dart';
import 'package:transportfiles/transfer/transfer.dart';
import 'package:transportfiles/transfer/publish.dart';

void main() {
  late Directory root;
  late TransferNode node;
  late ClientChannel channel;
  late String target;
  late Completer<List<File>> received;
  final failures = <String>[];
  setUp(() async {
    root = await Directory.systemTemp.createTemp('transport-test-');
    target = p.join(root.path, 'received');
    received = Completer<List<File>>();
    failures.clear();
    node = TransferNode(
      approve: (_, _) async => target,
      progress: (_, _) {},
      received: (files) {
        if (!received.isCompleted) received.complete(files);
      },
      failed: failures.add,
    );
    await node.start();
    channel = ClientChannel(
      '127.0.0.1',
      port: node.port,
      options: const ChannelOptions(credentials: ChannelCredentials.insecure()),
    );
  });
  tearDown(() async {
    await channel.terminate();
    await node.close();
    await root.delete(recursive: true);
  });
  wire.PairingRequest offer({String name = 'file.bin', int size = 3}) =>
      wire.PairingRequest(
        pCName: 'Test',
        fileName: name,
        sizeBytes: Int64(size),
        protocolVersion: 1,
      );
  Future<String> pair(wire.PairingRequest request) async =>
      (await wire.PairingServiceClient(channel).pairingRequest(request))
          .transferToken;
  Stream<wire.FileChunk> chunks(
    String token,
    String name,
    List<int> bytes, {
    List<int>? digest,
  }) async* {
    for (var i = 0; i < bytes.length; i += 65536) {
      yield wire.FileChunk(
        filename: name,
        transferToken: token,
        content: bytes.sublist(i, (i + 65536).clamp(0, bytes.length)),
      );
    }
    yield wire.FileChunk(
      filename: name,
      transferToken: token,
      sha256: digest ?? sha256.convert(bytes).bytes,
    );
  }

  Future<wire.TransferStatus> stream(Stream<wire.FileChunk> value) =>
      wire.FileTransferServiceClient(channel).sendFile(
        value,
        options: CallOptions(timeout: const Duration(seconds: 5)),
      );
  Future<void> clean() async {
    expect(
      await FileSystemEntity.type(target, followLinks: false),
      FileSystemEntityType.notFound,
    );
    expect(
      await root
          .list()
          .where((e) => p.basename(e.path).startsWith('.transport-'))
          .toList(),
      isEmpty,
    );
  }

  for (final length in [0, 3, 2 * 1024 * 1024 + 1]) {
    test('single transfer, $length bytes, Unicode filename', () async {
      final bytes = List.generate(length, (i) => i % 251);
      final source = await File(p.join(root.path, 'source'))
          .writeAsBytes(bytes);
      final client = TransferSender(Endpoint('127.0.0.1', node.port));
      try {
        await client.send(
          [SourceFile(source, 'Фотография 🌆.bin')],
          'Sender',
          () {},
          (_, _) {},
        );
      } finally {
        await client.close();
      }
      expect(await (await received.future).single.readAsBytes(), bytes);
    });
  }
  test('batch with duplicates and empty file', () async {
    final a = await File(p.join(root.path, 'a')).writeAsString('one');
    final b = await File(p.join(root.path, 'b')).writeAsString('');
    final client = TransferSender(Endpoint('127.0.0.1', node.port));
    try {
      await client.send(
        [SourceFile(a, 'Фото.txt'), SourceFile(b, 'Фото.txt')],
        'Sender',
        () {},
        (_, _) {},
      );
    } finally {
      await client.close();
    }
    final files = await received.future;
    expect(files.map((f) => p.basename(f.path)), ['Фото.txt', 'Фото (1).txt']);
    expect(await files[0].readAsString(), 'one');
    expect(await files[1].length(), 0);
  });
  test('refusal is propagated', () async {
    await node.close();
    node = TransferNode(
      approve: (_, _) async => null,
      progress: (_, _) {},
      received: (_) {},
      failed: failures.add,
    );
    await node.start();
    await channel.terminate();
    channel = ClientChannel(
      '127.0.0.1',
      port: node.port,
      options: const ChannelOptions(credentials: ChannelCredentials.insecure()),
    );
    expect(
      (await wire.PairingServiceClient(channel).pairingRequest(offer()))
          .response,
      isFalse,
    );
  });
  test(
    'unapproved and wrong-token streams rejected without consuming approval',
    () async {
      await expectLater(
        stream(chunks('no', 'file.bin', [1, 2, 3])),
        throwsA(isA<GrpcError>()),
      );
      final token = await pair(offer());
      await expectLater(
        stream(chunks('wrong', 'file.bin', [1, 2, 3])),
        throwsA(isA<GrpcError>()),
      );
      expect(
        (await stream(chunks(token, 'file.bin', [1, 2, 3]))).success,
        isTrue,
      );
    },
  );
  for (final variant in [
    'bad hash',
    'too short',
    'too long',
    'changed name',
    'missing hash',
  ]) {
    test('$variant removes all temporary data', () async {
      final token = await pair(offer());
      final data = switch (variant) {
        'bad hash' => chunks(token, 'file.bin', [
          1,
          2,
          3,
        ], digest: List.filled(32, 0)),
        'too short' => chunks(token, 'file.bin', [1]),
        'too long' => chunks(token, 'file.bin', [1, 2, 3, 4]),
        'changed name' => Stream.fromIterable([
          wire.FileChunk(
            filename: 'file.bin',
            transferToken: token,
            content: [1],
          ),
          wire.FileChunk(
            filename: 'other.bin',
            transferToken: token,
            content: [2, 3],
          ),
        ]),
        _ => Stream.value(
          wire.FileChunk(
            filename: 'file.bin',
            transferToken: token,
            content: [1, 2, 3],
          ),
        ),
      };
      await expectLater(stream(data), throwsA(isA<GrpcError>()));
      await clean();
    });
  }
  test('existing file created after approval survives', () async {
    final token = await pair(offer());
    await File(target).writeAsString('user data');
    await expectLater(
      stream(chunks(token, 'file.bin', [1, 2, 3])),
      throwsA(isA<GrpcError>()),
    );
    expect(await File(target).readAsString(), 'user data');
  });
  test('native no-replace preserves existing empty directory', () async {
    final source = await Directory(p.join(root.path, 'source')).create();
    await File(p.join(source.path, 'data')).writeAsString('new');
    await Directory(target).create();
    expect(
      () => publishWithoutReplacing(source.path, target),
      throwsA(isA<FileSystemException>()),
    );
    expect(await Directory(target).list().toList(), isEmpty);
    expect(await source.exists(), isTrue);
  });
  test('corrupt second file rolls back whole batch', () async {
    final request = offer(name: 'batch', size: 6)..protocolVersion = 2;
    request.files.addAll([
      wire.FileInfo(name: 'a', size: Int64(3)),
      wire.FileInfo(name: 'b', size: Int64(3)),
    ]);
    final token = await pair(request);
    Stream<wire.FileChunk> batch() async* {
      yield* chunks(token, 'a', [1, 2, 3]);
      yield* chunks(token, 'b', [4, 5, 6], digest: List.filled(32, 0));
    }

    await expectLater(stream(batch()), throwsA(isA<GrpcError>()));
    await clean();
  });
  test('cancel before first chunk releases receiver', () async {
    await pair(offer());
    node.cancelIncoming();
    final token = await pair(offer());
    expect(
      (await stream(chunks(token, 'file.bin', [1, 2, 3]))).success,
      isTrue,
    );
  });
  test(
    'receiver cancellation while sender stalls cleans partial file',
    () async {
      final token = await pair(offer());
      final controller = StreamController<wire.FileChunk>();
      final response = stream(controller.stream);
      final checked = expectLater(response, throwsA(isA<GrpcError>()));
      controller.add(
        wire.FileChunk(
          filename: 'file.bin',
          transferToken: token,
          content: [1],
        ),
      );
      for (var i = 0; i < 100 && (await root.list().toList()).isEmpty; i++) {
        await Future<void>.delayed(const Duration(milliseconds: 10));
      }
      node.cancelIncoming();
      await checked.timeout(const Duration(seconds: 3));
      await controller.close();
      await clean();
    },
  );
  test('cancel pending approval releases receiver', () async {
    await node.close();
    final pending = Completer<String?>();
    final asked = Completer<void>();
    node = TransferNode(
      approve: (_, cancelled) {
        asked.complete();
        return pending.future;
      },
      progress: (_, _) {},
      received: (_) {},
      failed: failures.add,
    );
    await node.start();
    await channel.terminate();
    channel = ClientChannel(
      '127.0.0.1',
      port: node.port,
      options: const ChannelOptions(credentials: ChannelCredentials.insecure()),
    );
    final response = wire.PairingServiceClient(channel).pairingRequest(offer());
    final checked = expectLater(response, throwsA(isA<GrpcError>()));
    await asked.future;
    await response.cancel();
    await checked;
    pending.complete(null);
  });
  test('reject unsafe names, normalization collisions, overflow and unsupported versions', () {
    for (final name in [
      '../x',
      '/x',
      r'C:\x',
      'NUL.txt',
      'a\u0000b',
      'x.',
      'x ',
      '',
      '😀' * 61,
    ]) {
      expect(() => validateName(name), throwsFormatException);
    }
    final request = offer(name: 'batch', size: 2)..protocolVersion = 2;
    request.files.addAll([
      wire.FileInfo(name: 'é', size: Int64(1)),
      wire.FileInfo(name: 'e\u0301', size: Int64(1)),
    ]);
    expect(() => Offer.parse(request), throwsFormatException);
    request.files[0].size = Int64.MAX_VALUE;
    request.files[1].name = 'b';
    expect(() => Offer.parse(request), throwsFormatException);
    expect(
      () => Offer.parse(offer()..protocolVersion = 99),
      throwsFormatException,
    );
    expect(() => Offer.parse(offer(size: -1)), throwsFormatException);
    expect(
      () => uniqueSources(List.generate(201, (_) => SourceFile(File('a')))),
      throwsFormatException,
    );
  });
  test('discovery uses packet source and rejects malformed announcements', () {
    final source = InternetAddress('192.168.1.5');
    final peer = parseAnnouncement(
      utf8.encode('TF1\nid\n1234\nMy Mac'),
      source,
      'own',
    );
    expect(peer!.endpoint.toString(), '192.168.1.5:1234');
    for (final value in [
      'TF1\nown\n1234\nMac',
      'TF1\nid\n0\nMac',
      'TF1\nid\n99999\nMac',
      'TF1\nid\n1234\nMac\nextra',
    ]) {
      expect(parseAnnouncement(utf8.encode(value), source, 'own'), isNull);
    }
    expect(Endpoint.parse('[::1]:5555').host, '::1');
  });
}
