import 'dart:ffi';
import 'dart:io';

import 'package:ffi/ffi.dart';

// Dart's rename replaces existing destinations on POSIX. Use the OS no-replace
// operation so a file created after approval can never be overwritten.
void publishWithoutReplacing(String source, String destination) {
  if (Platform.isWindows) {
    final move = DynamicLibrary.open('kernel32.dll')
        .lookupFunction<
          Int32 Function(Pointer<Utf16>, Pointer<Utf16>, Uint32),
          int Function(Pointer<Utf16>, Pointer<Utf16>, int)
        >('MoveFileExW');
    final from = source.toNativeUtf16(), to = destination.toNativeUtf16();
    try {
      if (move(from, to, 0) == 0) {
        throw FileSystemException(
          'Не удалось сохранить файл без перезаписи',
          destination,
        );
      }
    } finally {
      calloc.free(from);
      calloc.free(to);
    }
    return;
  }
  final libc = DynamicLibrary.process();
  final from = source.toNativeUtf8(), to = destination.toNativeUtf8();
  try {
    int result;
    if (Platform.isMacOS) {
      result = libc
          .lookupFunction<
            Int32 Function(Pointer<Utf8>, Pointer<Utf8>, Uint32),
            int Function(Pointer<Utf8>, Pointer<Utf8>, int)
          >('renamex_np')(from, to, 4);
    } else if (libc.providesSymbol('renameat2')) {
      result = libc
          .lookupFunction<
            Int32 Function(Int32, Pointer<Utf8>, Int32, Pointer<Utf8>, Uint32),
            int Function(int, Pointer<Utf8>, int, Pointer<Utf8>, int)
          >('renameat2')(-100, from, -100, to, 1);
    } else if (Platform.isAndroid) {
      // Android 8–10 have the kernel operation but no libc wrapper until API 30.
      final number = switch (Abi.current()) {
        Abi.androidArm => 382,
        Abi.androidArm64 => 276,
        Abi.androidX64 => 316,
        _ => throw UnsupportedError('Неподдерживаемая архитектура Android'),
      };
      result = libc
          .lookupFunction<
            IntPtr Function(
              IntPtr,
              IntPtr,
              Pointer<Utf8>,
              IntPtr,
              Pointer<Utf8>,
              IntPtr,
            ),
            int Function(int, int, Pointer<Utf8>, int, Pointer<Utf8>, int)
          >('syscall')(number, -100, from, -100, to, 1);
    } else {
      throw UnsupportedError(
        'Система не поддерживает сохранение без перезаписи',
      );
    }
    if (result != 0) {
      throw FileSystemException(
        'Не удалось сохранить: путь уже занят или папка недоступна',
        destination,
      );
    }
  } finally {
    calloc.free(from);
    calloc.free(to);
  }
}
