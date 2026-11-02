import 'dart:async';
import 'dart:io';
import 'dart:math';
import 'dart:typed_data';

import 'package:fixnum/fixnum.dart';
import 'package:grpc/grpc.dart';
import 'package:path/path.dart' as p;

import '../generated/transport_files.pbgrpc.dart' as wire;
import 'files.dart';
import 'publish.dart';
import 'bounded_grpc.dart';
import 'streaming_digest.dart';

class OfferedFile {
  final String name;
  final int size;
  const OfferedFile(this.name, this.size);
}

class Offer {
  final String sender, name;
  final List<OfferedFile> files;
  final int size;
  const Offer(this.sender, this.name, this.files, this.size);
  bool get multiple => files.length > 1;
  factory Offer.parse(wire.PairingRequest request) {
    if (request.protocolVersion != 1 && request.protocolVersion != 2) {
      throw const FormatException('Обновите приложение на обоих устройствах');
    }
    validateName(request.fileName);
    if (request.sizeBytes.isNegative) {
      throw const FormatException('Некорректный размер');
    }
    final files = request.protocolVersion == 1
        ? [OfferedFile(request.fileName, request.sizeBytes.toInt())]
        : request.files
              .map((f) => OfferedFile(validateName(f.name), f.size.toInt()))
              .toList();
    if ((request.protocolVersion == 1 && request.files.isNotEmpty) ||
        (request.protocolVersion == 2 &&
            (files.length < 2 || files.length > 200))) {
      throw const FormatException('Некорректный список файлов');
    }
    final names = <String>{};
    var total = 0;
    for (final file in files) {
      if (!names.add(nameKey(file.name)) ||
          file.size < 0 ||
          file.size > 0x7fffffffffffffff - total) {
        throw const FormatException('Некорректные имена или размеры');
      }
      total += file.size;
    }
    if (total != request.sizeBytes.toInt()) {
      throw const FormatException('Не совпадает общий размер');
    }
    return Offer(
      request.pCName,
      request.fileName,
      List.unmodifiable(files),
      total,
    );
  }
}

typedef Progress = void Function(int done, int total);
typedef Approval = Future<String?> Function(
  Offer offer,
  Future<void> cancelled,
);

class _Session {
  final Offer offer;
  final token = randomId();
  final cancelled = Completer<void>();
  String? target;
  bool started = false;
  Timer? timer;
  _Session(this.offer);
  void cancel() {
    timer?.cancel();
    if (!cancelled.isCompleted) cancelled.complete();
  }

  Future<T> whileActive<T>(Future<T> work) => Future.any([
    work,
    cancelled.future.then<T>(
      (_) => throw const GrpcError.cancelled('Передача отменена'),
    ),
  ]);
}

class TransferNode {
  final Approval approve;
  final Progress progress;
  final void Function(List<File> files) received;
  final void Function(String error) failed;
  late final BoundedServer _server;
  _Session? _current;
  TransferNode({
    required this.approve,
    required this.progress,
    required this.received,
    required this.failed,
  }) {
    _server = BoundedServer(services: [_Pairing(this), _Receiver(this)]);
  }
  int get port => _server.port!;
  Future<void> start({int port = 0}) async {
    // AppModel starts after the first frame. Load and verify the native backend
    // before advertising readiness, rather than on the first incoming file.
    final hash = StreamingDigest();
    try {
      await hash.finish();
    } finally {
      await hash.close();
    }
    await _server.bind(address: InternetAddress.anyIPv4, port: port);
  }

  void cancelIncoming() {
    final session = _current;
    session?.cancel();
    if (session != null && !session.started) _current = null;
  }

  Future<void> close() async {
    cancelIncoming();
    await _server.shutdown();
  }

  Future<wire.PairingResponse> _pair(
    ServiceCall call,
    wire.PairingRequest request,
  ) async {
    final Offer offer;
    try {
      offer = Offer.parse(request);
    } on FormatException catch (e) {
      throw GrpcError.failedPrecondition(e.message);
    }
    if (_current != null) {
      throw const GrpcError.resourceExhausted('Получатель занят');
    }
    final session = _Session(offer);
    _current = session;
    var accepted = false;
    final watch = Timer.periodic(const Duration(milliseconds: 100), (_) {
      if (call.isCanceled || call.isTimedOut) session.cancel();
    });
    try {
      final target = await session
          .whileActive(approve(offer, session.cancelled.future))
          .timeout(const Duration(seconds: 60));
      if (target == null) return wire.PairingResponse(response: false);
      if (call.isCanceled || session.cancelled.isCompleted) {
        throw const GrpcError.cancelled();
      }
      if (await FileSystemEntity.type(target, followLinks: false) !=
              FileSystemEntityType.notFound ||
          !await Directory(p.dirname(target)).exists()) {
        throw const FileSystemException(
          'Выберите новый файл в существующей папке',
        );
      }
      session.target = p.absolute(target);
      session.timer = Timer(const Duration(seconds: 60), () {
        if (!session.started && identical(_current, session)) {
          session.cancel();
          _current = null;
          failed('Отправитель не начал передачу');
        }
      });
      accepted = true;
      return wire.PairingResponse(response: true, transferToken: session.token);
    } catch (e) {
      failed(errorText(e));
      throw GrpcError.cancelled(errorText(e));
    } finally {
      watch.cancel();
      if (!accepted) {
        session.cancel();
        if (identical(_current, session)) _current = null;
      }
    }
  }

  Future<wire.TransferStatus> _receive(
    ServiceCall call,
    Stream<wire.FileChunk> chunks,
  ) async {
    _Session? session;
    Directory? temporary;
    RandomAccessFile? output;
    var index = 0, count = 0, completed = 0;
    var hash = StreamingDigest();
    final buffered = BytesBuilder(copy: false);
    Future<void> flushBuffered() async {
      if (buffered.isEmpty) return;
      final bytes = buffered.takeBytes();
      await output!.writeFrom(bytes);
      await hash.add(bytes);
    }

    final iterator = StreamIterator(chunks);
    final deadline = DateTime.now().add(const Duration(minutes: 10));
    try {
      while (true) {
        final next = iterator.moveNext().timeout(
          deadline.difference(DateTime.now()),
        );
        if (!(await (session?.whileActive(next) ?? next))) break;
        final chunk = iterator.current;
        if (session == null) {
          final candidate = _current;
          if (candidate == null ||
              candidate.target == null ||
              candidate.started ||
              candidate.cancelled.isCompleted ||
              candidate.token != chunk.transferToken ||
              candidate.offer.files.first.name != chunk.filename) {
            throw const FormatException('Нет разрешения на эту передачу');
          }
          session = candidate;
          session.started = true;
          session.timer?.cancel();
          temporary = await Directory(p.dirname(session.target!))
              .createTemp('.transport-');
        }
        if (session.cancelled.isCompleted ||
            call.isCanceled ||
            call.isTimedOut ||
            session.token != chunk.transferToken ||
            index >= session.offer.files.length ||
            session.offer.files[index].name != chunk.filename ||
            chunk.content.length > 64 * 1024) {
          throw const FormatException(
            'Некорректная последовательность блоков или передача отменена',
          );
        }
        final file = session.offer.files[index];
        output ??= await File(p.join(temporary!.path, file.name))
            .open(mode: FileMode.write);
        if (chunk.content.length > file.size - count) {
          throw const FormatException('Размер файла превышен');
        }
        if (chunk.sha256.isNotEmpty) {
          await flushBuffered();
          final digest = await hash.finish();
          if (chunk.content.isNotEmpty ||
              chunk.sha256.length != 32 ||
              count != file.size ||
              !_equal(digest, chunk.sha256)) {
            throw FormatException(
              'Файл повреждён или получен не полностью: ${file.name}',
            );
          }
          await output.flush();
          await output.close();
          output = null;
          completed += count;
          count = 0;
          index++;
          hash = StreamingDigest();
        } else {
          // Keep network messages bounded at 64 KiB, but amortize disk/FFI
          // scheduling over at most 1 MiB plus one incoming chunk.
          buffered.add(chunk.content);
          if (buffered.length >= 1024 * 1024) await flushBuffered();
          count += chunk.content.length;
          progress(completed + count, session.offer.size);
        }
      }
      if (session == null ||
          session.cancelled.isCompleted ||
          call.isCanceled ||
          call.isTimedOut ||
          index != session.offer.files.length ||
          completed != session.offer.size) {
        throw const FormatException('Файлы получены не полностью');
      }
      final source = session.offer.multiple
          ? temporary!.path
          : p.join(temporary!.path, session.offer.files.first.name);
      publishWithoutReplacing(source, session.target!);
      final files = session.offer.multiple
          ? session.offer.files
                .map((f) => File(p.join(session!.target!, f.name)))
                .toList()
          : [File(session.target!)];
      if (session.offer.multiple) temporary = null;
      received(files);
      return wire.TransferStatus(
        success: true,
        message: p.basename(session.target!),
      );
    } catch (e) {
      if (session != null) failed(errorText(e));
      throw GrpcError.dataLoss(errorText(e));
    } finally {
      await hash.close();
      await iterator.cancel();
      await output?.close();
      if (temporary != null && await temporary.exists()) {
        await temporary.delete(recursive: true);
      }
      session?.cancel();
      if (session != null && identical(_current, session)) _current = null;
    }
  }
}

class _Pairing extends wire.PairingServiceBase {
  final TransferNode node;
  _Pairing(this.node);
  @override
  Future<wire.PairingResponse> pairingRequest(
    ServiceCall call,
    wire.PairingRequest request,
  ) => node._pair(call, request);
}

class _Receiver extends wire.FileTransferServiceBase {
  final TransferNode node;
  _Receiver(this.node);
  @override
  Future<wire.TransferStatus> sendFile(
    ServiceCall call,
    Stream<wire.FileChunk> request,
  ) => node._receive(call, request);
}

bool _equal(List<int> a, List<int> b) {
  if (a.length != b.length) return false;
  var difference = 0;
  for (var i = 0; i < a.length; i++) {
    difference |= a[i] ^ b[i];
  }
  return difference == 0;
}

String errorText(Object error) => switch (error) {
  GrpcError e => e.message ?? 'Ошибка соединения (${e.code})',
  FormatException e => e.message,
  FileSystemException e => '${e.message}${e.path == null ? '' : ': ${e.path}'}',
  TimeoutException _ => 'Время ожидания истекло',
  _ => error.toString(),
};

class TransferSender {
  final Endpoint endpoint;
  late final ClientTransportConnectorChannel _channel;
  bool _cancelled = false;
  TransferSender(this.endpoint) {
    _channel = ClientTransportConnectorChannel(
      BoundedConnector(endpoint.host, endpoint.port),
      options: const ChannelOptions(
        credentials: ChannelCredentials.insecure(),
        connectionTimeout: Duration(seconds: 10),
      ),
    );
  }
  Future<void> close() async {
    _cancelled = true;
    await _channel.terminate();
  }

  Future<void> send(
    List<SourceFile> selection,
    String sender,
    void Function() accepted,
    Progress progress,
  ) async {
    final files = uniqueSources(selection);
    final sizes = <int>[];
    var total = 0;
    for (final file in files) {
      final stat = await file.file.stat();
      if (stat.type != FileSystemEntityType.file) {
        throw FileSystemException('Файл недоступен', file.file.path);
      }
      if (stat.size > 0x7fffffffffffffff - total) {
        throw const FormatException('Слишком большой общий размер');
      }
      sizes.add(stat.size);
      total += stat.size;
    }
    final request = wire.PairingRequest(
      pCName: sender,
      fileName: files.length == 1
          ? files.first.name
          : 'Файлы-${DateTime.now().millisecondsSinceEpoch}',
      fileSize: '$total',
      sizeBytes: Int64(total),
      protocolVersion: files.length == 1 ? 1 : 2,
      files: files.length == 1
          ? null
          : [
              for (var i = 0; i < files.length; i++)
                wire.FileInfo(name: files[i].name, size: Int64(sizes[i])),
            ],
    );
    try {
      if (_cancelled) throw const GrpcError.cancelled('Передача отменена');
      final approval = await wire.PairingServiceClient(_channel).pairingRequest(
        request,
        options: CallOptions(timeout: const Duration(seconds: 65)),
      );
      if (!approval.response) {
        throw const FormatException('Получатель отклонил запрос');
      }
      if (approval.transferToken.isEmpty) {
        throw const FormatException('Обновите приложение у получателя');
      }
      accepted();
      final status = await wire.FileTransferServiceClient(_channel).sendFile(
        _chunks(files, sizes, total, approval.transferToken, progress),
        options: CallOptions(timeout: const Duration(minutes: 10)),
      );
      if (!status.success) throw FormatException(status.message);
      progress(total, total);
    } on GrpcError catch (e) {
      if (e.code == StatusCode.unavailable) {
        throw FormatException(
          'Не удалось связаться с $endpoint. Проверьте, что приложение открыто и входящие соединения разрешены в firewall и VPN. ${e.message ?? ''}',
        );
      }
      rethrow;
    }
  }

  Stream<wire.FileChunk> _chunks(
    List<SourceFile> files,
    List<int> sizes,
    int total,
    String token,
    Progress progress,
  ) async* {
    var completed = 0;
    for (var i = 0; i < files.length; i++) {
      final input = await files[i].file.open();
      final hash = StreamingDigest();
      var count = 0;
      try {
        while (true) {
          if (_cancelled) throw const GrpcError.cancelled('Передача отменена');
          // Amortize asynchronous file reads and native hashing; keep the wire
          // messages at 64 KiB and check cancellation between every message.
          final bytes = await input.read(1024 * 1024);
          if (bytes.isEmpty) break;
          if (bytes.length > sizes[i] - count) {
            throw const FormatException('Размер исходного файла изменился');
          }
          await hash.add(bytes);
          for (var offset = 0; offset < bytes.length; offset += 64 * 1024) {
            if (_cancelled) {
              throw const GrpcError.cancelled('Передача отменена');
            }
            final part = Uint8List.sublistView(
              bytes,
              offset,
              min(offset + 64 * 1024, bytes.length),
            );
            count += part.length;
            yield wire.FileChunk(
              filename: files[i].name,
              transferToken: token,
              content: part,
            );
            progress(completed + count, total);
          }
        }
        if (count != sizes[i]) {
          throw const FormatException('Размер исходного файла изменился');
        }
        final digest = await hash.finish();
        yield wire.FileChunk(
          filename: files[i].name,
          transferToken: token,
          sha256: digest,
        );
        completed += count;
      } finally {
        try {
          await hash.close();
        } finally {
          await input.close();
        }
      }
    }
  }
}
