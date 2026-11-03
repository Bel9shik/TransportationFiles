import 'dart:async';
import 'dart:io';
import 'dart:isolate';
import 'dart:typed_data';

import 'streaming_digest.dart';

class PreparedFilePart {
  final Uint8List bytes;
  final bool isDigest;
  PreparedFilePart(this.bytes, {required this.isDigest});
}

/// A reusable reader/hash worker with one 1-MiB block prefetched at a time.
/// The next block is read and hashed while the caller sends the current one.
class PreparedFileSource {
  Future<_ReaderWorker>? _starting;
  Future<void>? _closing;
  bool _closed = false;
  bool _reading = false;

  Stream<PreparedFilePart> readFile(File file, int expectedSize) async* {
    if (_closed) throw StateError('Чтение файлов уже закрыто');
    if (_reading) throw StateError('Предыдущее чтение ещё не завершено');
    _reading = true;
    _ReaderWorker? worker;
    Future<Object?>? pending;
    try {
      worker = await (_starting ??= _ReaderWorker.start());
      if (_closed) return;
      await worker.request('open', path: file.path, size: expectedSize);
      if (_closed) return;
      pending = worker.request('read');
      while (!_closed) {
        final reply = await pending! as _PartReply;
        if (_closed) return;
        final part = PreparedFilePart(
          reply.bytes.materialize().asUint8List(),
          isDigest: reply.isDigest,
        );
        if (part.isDigest) {
          pending = null;
          yield part;
          return;
        }
        // No read beyond this one is requested until the caller resumes this
        // generator. Observe errors immediately, even while the caller pauses.
        pending = worker.request('read');
        pending.ignore();
        yield part;
      }
    } finally {
      if (pending != null) {
        await pending.then<void>((_) {}, onError: (Object _, StackTrace _) {});
      }
      try {
        if (_closed) {
          await _closing;
        } else {
          await worker?.request('cancel');
        }
      } finally {
        _reading = false;
      }
    }
  }

  Future<void> close() {
    _closed = true;
    return _closing ??= _closeWorker();
  }

  Future<void> _closeWorker() async {
    final starting = _starting;
    if (starting == null) return;
    final worker = await starting;
    await worker.close();
  }
}

class _Command {
  final int id;
  final String operation;
  final String? path;
  final int? size;
  _Command(this.id, this.operation, this.path, this.size);
}

class _Response {
  final int id;
  final Object? value;
  final Object? error;
  final String? stack;
  _Response(this.id, this.value) : error = null, stack = null;
  _Response.error(this.id, this.error, StackTrace stackTrace)
    : value = null,
      stack = stackTrace.toString();
}

class _PartReply {
  final TransferableTypedData bytes;
  final bool isDigest;
  _PartReply(Uint8List data, {required this.isDigest})
    : bytes = TransferableTypedData.fromList([data]);
}

class _ReaderWorker {
  final _responses = ReceivePort();
  final _exits = ReceivePort();
  final _errors = ReceivePort();
  final _ready = Completer<void>();
  final _exited = Completer<void>();
  final _pending = <int, Completer<Object?>>{};
  late final SendPort _commands;
  var _nextId = 0;
  int? _shutdownId;
  bool _dead = false;
  Future<void>? _closing;

  _ReaderWorker() {
    _ready.future.ignore();
    _responses.listen((message) {
      if (message is SendPort) {
        if (_ready.isCompleted) return;
        _commands = message;
        _ready.complete();
      } else if (message is _Response) {
        final waiting = _pending.remove(message.id);
        if (waiting == null) return;
        if (message.error != null) {
          waiting.completeError(
            message.error!,
            StackTrace.fromString(message.stack!),
          );
        } else {
          waiting.complete(message.value);
        }
      }
    });
    _errors.listen((message) {
      final error = message as List<dynamic>;
      _fail(StateError('Ошибка фонового чтения: ${error.first}'));
    });
    _exits.listen((_) {
      // Isolate exit and replies use different ports, so their delivery order
      // is unspecified. Exit itself acknowledges that close released handles.
      _pending.remove(_shutdownId)?.complete(null);
      _fail(StateError('Фоновое чтение файла завершилось'));
      _responses.close();
      _errors.close();
      _exits.close();
      _exited.complete();
    });
  }

  static Future<_ReaderWorker> start() async {
    final worker = _ReaderWorker();
    try {
      await Isolate.spawn(
        _readFiles,
        worker._responses.sendPort,
        onExit: worker._exits.sendPort,
        onError: worker._errors.sendPort,
        debugName: 'transport-file-reader',
      );
      await worker._ready.future;
      return worker;
    } catch (_) {
      worker._responses.close();
      worker._errors.close();
      worker._exits.close();
      rethrow;
    }
  }

  void _fail(Object error) {
    _dead = true;
    if (!_ready.isCompleted) _ready.completeError(error);
    for (final waiting in _pending.values) {
      waiting.completeError(error);
    }
    _pending.clear();
  }

  Future<Object?> request(String operation, {String? path, int? size}) {
    if (_dead) return Future.error(StateError('Фоновое чтение уже закрыто'));
    final id = _nextId++;
    final waiting = Completer<Object?>();
    waiting.future.ignore();
    _pending[id] = waiting;
    if (operation == 'close') _shutdownId = id;
    _commands.send(_Command(id, operation, path, size));
    return waiting.future;
  }

  Future<void> close() => _closing ??= () async {
    try {
      if (!_dead) await request('close');
    } finally {
      await _exited.future;
    }
  }();
}

// Keep all file/digest ownership here. await-for serializes commands even when
// native digest initialization or hashing yields to the isolate's event loop.
Future<void> _readFiles(SendPort responses) async {
  final commands = ReceivePort();
  responses.send(commands.sendPort);
  RandomAccessFile? input;
  StreamingDigest? hash;
  var count = 0, expected = 0;

  Future<void> closeFile() async {
    try {
      await hash?.close();
    } finally {
      hash = null;
      input?.closeSync();
      input = null;
    }
  }

  try {
    await for (final message in commands) {
      final command = message as _Command;
      Object? result;
      try {
        switch (command.operation) {
          case 'open':
            await closeFile();
            input = File(command.path!).openSync();
            expected = command.size!;
            count = 0;
            if (input!.lengthSync() != expected) {
              throw const FormatException('Размер исходного файла изменился');
            }
            hash = StreamingDigest();
          case 'read':
            final bytes = input!.readSync(1024 * 1024);
            if (bytes.isEmpty) {
              if (count != expected) {
                throw const FormatException('Размер исходного файла изменился');
              }
              final digest = Uint8List.fromList(await hash!.finish());
              await closeFile();
              result = _PartReply(digest, isDigest: true);
            } else {
              if (bytes.length > expected - count) {
                throw const FormatException('Размер исходного файла изменился');
              }
              await hash!.add(bytes);
              count += bytes.length;
              result = _PartReply(bytes, isDigest: false);
            }
          case 'cancel':
          case 'close':
            await closeFile();
          default:
            throw StateError('Unknown file reader command');
        }
        responses.send(_Response(command.id, result));
      } catch (error, stack) {
        await closeFile();
        responses.send(_Response.error(command.id, error, stack));
      }
      if (command.operation == 'close') break;
    }
  } finally {
    commands.close();
    await closeFile();
  }
}
