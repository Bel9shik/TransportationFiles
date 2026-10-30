import 'dart:io';
import 'dart:ui' as ui;

import 'package:file_picker/file_picker.dart';
import 'package:flutter/services.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:super_clipboard/super_clipboard.dart';

import 'transfer/files.dart';

class SystemFiles {
  static const channel = MethodChannel('transportfiles/system');
  late final preferences = SharedPreferencesAsync();
  String? folder;
  late String downloads;
  late String receivedDirectory;
  String get folderLabel => folder == null
      ? downloads
      : Platform.isAndroid
      ? Uri.decodeFull(folder!)
      : folder!;
  Future<void> load() async {
    folder = await preferences.getString('downloadDirectory');
    if (Platform.isAndroid) {
      downloads = 'Загрузки / TransportationFiles';
      receivedDirectory = (await channel.invokeMethod<String>(
        'receivedDirectory',
      ))!;
    } else {
      downloads =
          (await getDownloadsDirectory())?.path ??
          p.join(
            Platform.environment['HOME'] ??
                Platform.environment['USERPROFILE']!,
            'Downloads',
          );
      receivedDirectory = p.join(
        (await getApplicationSupportDirectory()).path,
        'received',
      );
    }
  }

  Future<String?> chooseFolder() => Platform.isAndroid
      ? channel.invokeMethod<String>('chooseFolder')
      : FilePicker.getDirectoryPath(dialogTitle: 'Папка для файлов');
  Future<void> setFolder(String? value) async {
    if (value == null) {
      await preferences.remove('downloadDirectory');
    } else {
      await preferences.setString('downloadDirectory', value);
    }
    folder = value;
  }

  Future<String> target(
    String name, {
    String? chosenFolder,
    String? exactPath,
  }) async {
    if (Platform.isAndroid) {
      if (chosenFolder == null &&
          folder == null &&
          await channel.invokeMethod<bool>('downloadsPermission') != true) {
        throw const FileSystemException(
          'Разрешите сохранение в Загрузки или выберите другую папку',
        );
      }
      final directory = await Directory(receivedDirectory)
          .create(recursive: true);
      final incoming = await directory.createTemp('');
      return p.join(incoming.path, name);
    }
    if (exactPath != null) return exactPath;
    final directory = chosenFolder ?? folder ?? downloads;
    if (chosenFolder == null && folder == null) {
      await Directory(directory).create(recursive: true);
    }
    if (!await Directory(directory).exists()) {
      throw const FileSystemException('Выбранная папка недоступна');
    }
    return unusedPath(directory, name);
  }

  Future<void> exportAndroid(List<File> files, String? destination) async {
    await channel.invokeMethod('exportFiles', {
      'paths': files.map((f) => f.path).toList(),
      'folder': destination,
    });
  }

  Future<void> exportAs(File file) async {
    if (Platform.isAndroid) {
      await channel.invokeMethod<String>('exportAs', {'path': file.path});
    } else {
      final path = await FilePicker.saveFile(
        fileName: p.basename(file.path),
        dialogTitle: 'Сохранить копию',
      );
      if (path != null) {
        // The native save dialog explicitly confirms replacing an existing file.
        if (p.normalize(path) != p.normalize(file.path)) await file.copy(path);
      }
    }
  }

  Future<List<File>> recent() async {
    if (!Platform.isAndroid) {
      final paths = await preferences.getStringList('recentFiles') ?? [];
      final result = <File>[];
      for (final path in paths) {
        if (await File(path).exists()) result.add(File(path));
      }
      return result;
    }
    final directory = Directory(receivedDirectory);
    if (!await directory.exists()) return [];
    return await directory
        .list(recursive: true, followLinks: false)
        .where(
          (f) =>
              f is File &&
              !p.split(f.path).any((part) => part.startsWith('.transport-')),
        )
        .cast<File>()
        .toList();
  }

  Future<void> remember(List<File> files) async {
    if (Platform.isAndroid) return;
    final previous = await preferences.getStringList('recentFiles') ?? [];
    await preferences.setStringList(
      'recentFiles',
      {...files.map((f) => f.path), ...previous}.take(200).toList(),
    );
  }

  Future<void> copy(List<File> files) async {
    if (files.isEmpty) return;
    if (Platform.isAndroid) {
      await channel.invokeMethod('copyFiles', {
        'paths': files.map((f) => f.path).toList(),
      });
      return;
    }
    final clipboard = SystemClipboard.instance;
    if (clipboard == null) throw UnsupportedError('Буфер обмена недоступен');
    final items = <DataWriterItem>[];
    for (final file in files) {
      final item = DataWriterItem(suggestedName: p.basename(file.path));
      item.add(Formats.fileUri(file.uri));
      if (files.length == 1 &&
          {
            '.png',
            '.jpg',
            '.jpeg',
            '.gif',
            '.bmp',
            '.webp',
          }.contains(p.extension(file.path).toLowerCase())) {
        final buffer = await ui.ImmutableBuffer.fromFilePath(file.path);
        ui.ImageDescriptor? descriptor;
        ui.Codec? codec;
        ui.Image? image;
        try {
          descriptor = await ui.ImageDescriptor.encoded(buffer);
          if (descriptor.width * descriptor.height > 40000000) {
            throw const FormatException(
              'Изображение больше 40 Мп. Используйте «Поделиться» или сохраните файл.',
            );
          }
          codec = await descriptor.instantiateCodec();
          image = (await codec.getNextFrame()).image;
          final bytes = await image.toByteData(format: ui.ImageByteFormat.png);
          if (bytes == null) {
            throw const FormatException('Не удалось скопировать изображение');
          }
          item.add(Formats.png(bytes.buffer.asUint8List()));
        } finally {
          image?.dispose();
          codec?.dispose();
          descriptor?.dispose();
          buffer.dispose();
        }
      }
      items.add(item);
    }
    await clipboard.write(items);
  }
}
