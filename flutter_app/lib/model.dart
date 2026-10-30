import 'dart:async';
import 'dart:io';

import 'package:flutter/foundation.dart';

import 'transfer/discovery.dart';
import 'transfer/files.dart';
import 'transfer/transfer.dart';
import 'system_files.dart';

class SaveChoice {
  final String? folder, exactPath;
  const SaveChoice({this.folder, this.exactPath});
}

enum Screen { home, send, receive, transfer }

class AppModel extends ChangeNotifier {
  final SystemFiles storage;
  final Future<SaveChoice?> Function(Offer, Future<void>) ask;
  AppModel(this.storage, this.ask);
  TransferNode? node;
  Discovery? discovery;
  TransferSender? sender;
  Screen screen = Screen.home;
  bool ready = false,
      busy = false,
      success = false,
      incoming = false,
      _disposed = false;
  String status = '', folderLabel = '', notice = '';
  int done = 0, total = 0;
  List<SourceFile> selected = [];
  List<File> received = [], recent = [];
  String? _receiveFolder;
  final name = Platform.localHostname;
  void update() {
    if (!_disposed) notifyListeners();
  }

  Future<void> start() async {
    try {
      await storage.load();
      folderLabel = storage.folderLabel;
      node = TransferNode(
        approve: _approve,
        progress: (a, b) {
          done = a;
          total = b;
          update();
        },
        received: (files) {
          unawaited(_received(files));
        },
        failed: fail,
      );
      await node!.start();
      discovery = Discovery(node!.port, name, update);
      ready = true;
      final probe = Platform.environment['TRANSPORT_READY_PROBE'];
      if (probe != null) await File(probe).writeAsString('${node!.port}');
      update();
      unawaited(discovery!.start());
      recent = await storage.recent();
      update();
    } catch (e) {
      notice = errorText(e);
      update();
    }
  }

  Future<String?> _approve(Offer offer, Future<void> cancelled) async {
    if (busy || (screen != Screen.receive && !Platform.isAndroid)) return null;
    busy = true;
    update();
    var aborted = false;
    unawaited(
      cancelled.then((_) {
        aborted = true;
      }),
    );
    try {
      final choice = await ask(offer, cancelled);
      if (choice == null) {
        busy = false;
        update();
        return null;
      }
      final path = await storage.target(
        offer.name,
        chosenFolder: choice.folder,
        exactPath: choice.exactPath,
      );
      if (aborted) throw const FormatException('Отправитель отменил запрос');
      _receiveFolder = choice.folder ?? storage.folder;
      incoming = true;
      success = false;
      received = [];
      done = 0;
      total = offer.size;
      status = 'Ожидание отправителя…';
      screen = Screen.transfer;
      update();
      return path;
    } catch (e) {
      busy = false;
      update();
      rethrow;
    }
  }

  Future<void> _received(List<File> files) async {
    received = files;
    try {
      if (Platform.isAndroid) {
        status = 'Сохранение в выбранную папку…';
        update();
        await storage.exportAndroid(files, _receiveFolder);
      }
      await storage.remember(files);
      recent = await storage.recent();
      success = true;
      status = files.length == 1 ? 'Файл получен' : 'Файлы получены';
    } catch (e) {
      status =
          'Файлы получены в приложении. Не удалось сохранить копию: ${errorText(e)}. Используйте «Сохранить как». ';
      recent = [...files, ...recent];
    }
    busy = false;
    update();
  }

  Future<void> send(Endpoint endpoint) async {
    if (busy || selected.isEmpty) return;
    busy = true;
    incoming = false;
    success = false;
    done = 0;
    total = 0;
    status = 'Ожидание подтверждения…';
    screen = Screen.transfer;
    update();
    final client = TransferSender(endpoint);
    sender = client;
    try {
      await client.send(
        selected,
        name,
        () {
          status = 'Отправка файлов…';
          update();
        },
        (a, b) {
          done = a;
          total = b;
          if (a == b) status = 'Получатель проверяет и сохраняет файлы…';
          update();
        },
      );
      success = true;
      status = 'Отправка завершена';
    } catch (e) {
      status = errorText(e);
    } finally {
      await client.close();
      if (identical(sender, client)) sender = null;
      busy = false;
      update();
    }
  }

  void fail(String error) {
    busy = false;
    success = false;
    status = error;
    screen = Screen.transfer;
    update();
  }

  void cancel() {
    node?.cancelIncoming();
    unawaited(sender?.close());
  }

  void navigate(Screen value) {
    if (busy) return;
    screen = value;
    notice = '';
    update();
  }

  Future<void> changeFolder(String? value) async {
    await storage.setFolder(value);
    folderLabel = storage.folderLabel;
    update();
  }

  @override
  void dispose() {
    _disposed = true;
    discovery?.close();
    unawaited(node?.close());
    unawaited(sender?.close());
    super.dispose();
  }
}
