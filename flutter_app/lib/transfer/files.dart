import 'dart:convert';
import 'dart:io';
import 'dart:math';

import 'package:path/path.dart' as p;
import 'package:unorm_dart/unorm_dart.dart' as unicode;

String validateName(String name) {
  if (name.isEmpty ||
      utf8.encode(name).length > 240 ||
      name == '.' ||
      name == '..' ||
      name.endsWith('.') ||
      name.endsWith(' ') ||
      RegExp(r'[\\/:*?"<>|\x00-\x1f\x7f]').hasMatch(name) ||
      RegExp(
        r'^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\..*)?$',
        caseSensitive: false,
      ).hasMatch(name)) {
    throw FormatException('Недопустимое имя файла: $name');
  }
  return name;
}

String nameKey(String name) => unicode.nfc(name).toLowerCase();
String randomId() {
  final random = Random.secure();
  return List.generate(
    24,
    (_) => random.nextInt(256).toRadixString(16).padLeft(2, '0'),
  ).join();
}

class SourceFile {
  final File file;
  final String name;
  SourceFile(this.file, [String? name]) : name = name ?? p.basename(file.path);
}

List<SourceFile> uniqueSources(List<SourceFile> files) {
  if (files.isEmpty || files.length > 200) {
    throw const FormatException('Выберите от 1 до 200 файлов');
  }
  final names = <String>{};
  return files.map((file) {
    var name = validateName(file.name);
    final extension = p.extension(name);
    final stem = p.basenameWithoutExtension(name);
    var suffix = 1;
    while (!names.add(nameKey(name))) {
      name = '$stem (${suffix++})$extension';
    }
    return SourceFile(file.file, validateName(name));
  }).toList();
}

Future<String> unusedPath(String directory, String name) async {
  validateName(name);
  final extension = p.extension(name);
  final stem = p.basenameWithoutExtension(name);
  for (var n = 0; n < 10000; n++) {
    final candidate = p.join(directory, n == 0 ? name : '$stem ($n)$extension');
    if (await FileSystemEntity.type(candidate, followLinks: false) ==
        FileSystemEntityType.notFound) {
      return candidate;
    }
  }
  throw const FileSystemException('Не удалось подобрать свободное имя');
}

class Endpoint {
  final String host;
  final int port;
  const Endpoint(this.host, this.port);
  factory Endpoint.parse(String value) {
    final match = RegExp(r'^(?:\[([^\]]+)\]|([^:\s/]+)):(\d+)$')
        .firstMatch(value.trim());
    if (match == null) {
      throw const FormatException(
        'Укажите адрес:порт, например 192.168.1.5:50071',
      );
    }
    final port = int.parse(match[3]!);
    if (port < 1 || port > 65535) {
      throw const FormatException('Порт должен быть от 1 до 65535');
    }
    return Endpoint(match[1] ?? match[2]!, port);
  }
  @override
  String toString() => '${host.contains(':') ? '[$host]' : host}:$port';
}
