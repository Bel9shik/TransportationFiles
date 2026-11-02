import 'dart:ffi';
import 'dart:math';
import 'dart:typed_data';

import 'package:ffi/ffi.dart';

/// SHA-256 through Windows 10+ CNG, with one reusable 1-MiB native input buffer.
/// https://learn.microsoft.com/windows/win32/seccng/cng-algorithm-pseudo-handles
class WindowsDigest {
  static final _dll = DynamicLibrary.open('bcrypt.dll');
  static final _create = _dll
      .lookupFunction<
        Int32 Function(
          Pointer<Void>,
          Pointer<Pointer<Void>>,
          Pointer<Uint8>,
          Uint32,
          Pointer<Uint8>,
          Uint32,
          Uint32,
        ),
        int Function(
          Pointer<Void>,
          Pointer<Pointer<Void>>,
          Pointer<Uint8>,
          int,
          Pointer<Uint8>,
          int,
          int,
        )
      >('BCryptCreateHash');
  static final _update = _dll
      .lookupFunction<
        Int32 Function(Pointer<Void>, Pointer<Uint8>, Uint32, Uint32),
        int Function(Pointer<Void>, Pointer<Uint8>, int, int)
      >('BCryptHashData');
  static final _finish = _dll
      .lookupFunction<
        Int32 Function(Pointer<Void>, Pointer<Uint8>, Uint32, Uint32),
        int Function(Pointer<Void>, Pointer<Uint8>, int, int)
      >('BCryptFinishHash');
  static final _destroy = _dll
      .lookupFunction<
        Int32 Function(Pointer<Void>),
        int Function(Pointer<Void>)
      >('BCryptDestroyHash');
  static const _capacity = 1024 * 1024;
  Pointer<Void> _handle = nullptr;
  Pointer<Uint8> _buffer = nullptr;
  bool _closed = false;
  Uint8List? _digest;

  void _check(int status) {
    if (status != 0) {
      throw StateError(
        'Windows SHA-256 failed: 0x${status.toUnsigned(32).toRadixString(16)}',
      );
    }
  }

  void _ensureOpen() {
    if (_closed) throw StateError('Digest is closed');
    if (_handle != nullptr) return;
    final result = calloc<Pointer<Void>>();
    try {
      // BCRYPT_SHA256_ALG_HANDLE is a shared OS-owned pseudo-handle. CNG
      // allocates each hash object and BCryptDestroyHash releases that object.
      _check(
        _create(
          Pointer<Void>.fromAddress(0x41),
          result,
          nullptr,
          0,
          nullptr,
          0,
          0,
        ),
      );
      _handle = result.value;
    } finally {
      calloc.free(result);
    }
  }

  void add(List<int> bytes) {
    _ensureOpen();
    if (bytes.isEmpty) return;
    if (_buffer == nullptr) _buffer = malloc<Uint8>(_capacity);
    for (var offset = 0; offset < bytes.length; offset += _capacity) {
      final count = min(_capacity, bytes.length - offset);
      final part = bytes is Uint8List
          ? Uint8List.sublistView(bytes, offset, offset + count)
          : bytes.sublist(offset, offset + count);
      _buffer.asTypedList(count).setAll(0, part);
      _check(_update(_handle, _buffer, count, 0));
    }
  }

  Uint8List finish() {
    if (_digest != null) return _digest!;
    _ensureOpen();
    final output = malloc<Uint8>(32);
    try {
      _check(_finish(_handle, output, 32, 0));
      return _digest = Uint8List.fromList(output.asTypedList(32));
    } finally {
      malloc.free(output);
      close();
    }
  }

  void close() {
    _closed = true;
    if (_handle != nullptr) {
      _destroy(_handle);
      _handle = nullptr;
    }
    if (_buffer != nullptr) {
      malloc.free(_buffer);
      _buffer = nullptr;
    }
  }
}
