import 'dart:async';
import 'dart:collection';
import 'dart:math';
import 'dart:typed_data';

import 'package:webcrypto/webcrypto.dart';

/// Native SHA-256 with at most one caller-supplied chunk in flight.
class StreamingDigest {
  var _listened = false;
  late final _input = StreamController<List<int>>(
    sync: true,
    onListen: () => _listened = true,
  );
  late final Future<List<int>> _result;
  Object? _error;
  StackTrace? _stack;

  StreamingDigest() {
    _result = Future.sync(() => Hash.sha256.digestStream(_input.stream));
    // Observe initialization errors even before the first chunk is supplied.
    unawaited(
      _result.then<void>(
        (_) {},
        onError: (Object e, StackTrace s) {
          _error = e;
          _stack = s;
          // A native initialization failure can happen before anyone listens.
          // Drain our bounded input so a pending addStream/close cannot hang.
          if (!_listened) unawaited(_input.stream.drain<void>());
        },
      ),
    );
  }

  Future<void> add(List<int> bytes) async {
    if (_error != null) Error.throwWithStackTrace(_error!, _stack!);
    // addStream propagates the native digest stream's pause/resume. add alone
    // could queue the entire file when hashing cannot keep up with its producer.
    final data = bytes is Uint8List ? bytes : Uint8List.fromList(bytes);
    await _input.addStream(Stream.value(_DigestBytes(data)));
    if (_error != null) Error.throwWithStackTrace(_error!, _stack!);
  }

  Future<List<int>> finish() async {
    await _input.close();
    return _result;
  }

  Future<void> close() async {
    await _input.close();
    await _result.then<void>((_) {}, onError: (Object _, StackTrace _) {});
  }
}

// webcrypto streams through skip/take slices before copying into its native
// buffer. Preserve typed views so setAll uses a bulk byte copy, not an iterator
// call per byte. This uses only the public List/Iterable contract.
class _DigestBytes extends ListBase<int> {
  final Uint8List bytes;
  _DigestBytes(this.bytes);
  @override
  int get length => bytes.length;
  @override
  set length(int value) => throw UnsupportedError('Read-only digest input');
  @override
  int operator [](int index) => bytes[index];
  @override
  void operator []=(int index, int value) =>
      throw UnsupportedError('Read-only digest input');
  @override
  Iterable<int> skip(int count) {
    RangeError.checkNotNegative(count);
    return _DigestBytes(Uint8List.sublistView(bytes, min(count, length)));
  }

  @override
  Iterable<int> take(int count) {
    RangeError.checkNotNegative(count);
    return Uint8List.sublistView(bytes, 0, min(count, length));
  }
}
