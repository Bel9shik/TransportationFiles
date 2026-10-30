import 'dart:async';
import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:path/path.dart' as p;
import 'package:share_plus/share_plus.dart';

import 'model.dart';
import 'system_files.dart';
import 'transfer/files.dart';
import 'transfer/transfer.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  if (Platform.isMacOS) FilePicker.skipEntitlementsChecks();
  runApp(const TransportApp());
  WidgetsBinding.instance.addPostFrameCallback((_) async {
    final marker = Platform.environment['TRANSPORT_STARTUP_PROBE'];
    if (marker != null) await File(marker).writeAsString('ready');
  });
}

class TransportApp extends StatelessWidget {
  final AppModel? controller;
  const TransportApp({super.key, this.controller});
  @override
  Widget build(BuildContext context) => MaterialApp(
    title: 'TransportationFiles',
    debugShowCheckedModeBanner: false,
    theme: ThemeData(
      useMaterial3: true,
      colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xff315fa0)),
      scaffoldBackgroundColor: const Color(0xff142c49),
      inputDecorationTheme: const InputDecorationTheme(
        border: OutlineInputBorder(
          borderRadius: BorderRadius.all(Radius.circular(16)),
        ),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          minimumSize: const Size(48, 52),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
          ),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          minimumSize: const Size(48, 48),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
          ),
        ),
      ),
    ),
    home: HomePage(controller: controller),
  );
}

class HomePage extends StatefulWidget {
  final AppModel? controller;
  const HomePage({super.key, this.controller});
  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> with WidgetsBindingObserver {
  late final AppModel model;
  final manual = TextEditingController();
  bool actionRunning = false;
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    model =
        widget.controller ??
        AppModel(
          SystemFiles(),
          (offer, cancelled) => showDialog<SaveChoice>(
            context: context,
            barrierDismissible: false,
            builder: (_) => ReceiveDialog(
              offer: offer,
              cancelled: cancelled,
              storage: model.storage,
            ),
          ),
        );
    model.addListener(_changed);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) unawaited(model.start());
    });
  }

  void _changed() {
    if (mounted) setState(() {});
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      unawaited(model.discovery?.refresh());
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    model.removeListener(_changed);
    model.dispose();
    manual.dispose();
    super.dispose();
  }

  Future<void> action(Future<void> Function() work, {String? completed}) async {
    if (actionRunning) return;
    setState(() {
      actionRunning = true;
      model.notice = '';
    });
    try {
      await work();
      if (mounted && completed != null) model.notice = completed;
    } catch (e) {
      if (mounted) model.notice = errorText(e);
    } finally {
      if (mounted) {
        setState(() {
          actionRunning = false;
        });
      }
    }
  }

  Future<void> pick() async {
    final result = await FilePicker.pickFiles(
      allowMultiple: true,
      withData: false,
    );
    if (result == null || !mounted) return;
    if (result.files.any((f) => f.path == null)) {
      throw const FormatException('Выбранный файл недоступен');
    }
    model.selected = uniqueSources(
      result.files.map((f) => SourceFile(File(f.path!), f.name)).toList(),
    );
    model.navigate(Screen.send);
  }

  Future<List<File>?> chooseFiles(List<File> files) => showDialog<List<File>>(
    context: context,
    builder: (_) => FileSelection(files: files),
  );
  Future<void> copy(List<File> files) async {
    final selected = await chooseFiles(files);
    if (selected == null) return;
    await model.storage.copy(selected);
    if (mounted) {
      setState(() {
        model.notice = selected.length == 1
            ? 'Скопировано в буфер обмена'
            : 'Файлы скопированы в буфер обмена';
      });
    }
  }

  Future<void> share(List<File> files) async {
    final selected = await chooseFiles(files);
    if (selected != null) {
      await SharePlus.instance.share(
        ShareParams(files: selected.map((f) => XFile(f.path)).toList()),
      );
    }
  }

  Widget card(List<Widget> children) => Card(
    color: Colors.white,
    margin: EdgeInsets.zero,
    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(26)),
    child: Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: children,
      ),
    ),
  );
  Widget title(String text) => Padding(
    padding: const EdgeInsets.only(bottom: 12),
    child: Text(
      text,
      style: const TextStyle(fontSize: 24, fontWeight: FontWeight.w700),
    ),
  );
  Widget paragraph(String text) => Padding(
    padding: const EdgeInsets.only(bottom: 16),
    child: Text(text, style: const TextStyle(fontSize: 16, height: 1.45)),
  );
  Widget fileActions(List<File> files) => Wrap(
    spacing: 10,
    runSpacing: 10,
    children: [
      OutlinedButton.icon(
        onPressed: actionRunning ? null : () => action(() => copy(files)),
        icon: const Icon(Icons.copy_rounded),
        label: const Text('В буфер обмена'),
      ),
      if (Platform.isAndroid)
        OutlinedButton.icon(
          onPressed: actionRunning ? null : () => action(() => share(files)),
          icon: const Icon(Icons.share_rounded),
          label: const Text('Поделиться'),
        ),
      OutlinedButton.icon(
        onPressed: actionRunning
            ? null
            : () => action(() async {
                final selected = await chooseFiles(files);
                if (selected == null) return;
                if (Platform.isAndroid && selected.length > 1) {
                  final folder = await model.storage.chooseFolder();
                  if (folder != null) {
                    await model.storage.exportAndroid(selected, folder);
                  }
                } else {
                  for (final file in selected) {
                    await model.storage.exportAs(file);
                  }
                }
              }),
        icon: const Icon(Icons.save_alt_rounded),
        label: const Text('Сохранить как'),
      ),
    ],
  );
  Widget home() => Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      card([
        title('Файлы рядом'),
        paragraph(
          'Отправляйте фотографии и файлы между устройствами в одной локальной сети.',
        ),
        FilledButton.icon(
          style: FilledButton.styleFrom(
            backgroundColor: const Color(0xffc43d35),
          ),
          onPressed: !model.ready || actionRunning ? null : () => action(pick),
          icon: const Icon(Icons.north_east_rounded),
          label: const Text('Отправить файлы'),
        ),
        const SizedBox(height: 12),
        FilledButton.icon(
          style: FilledButton.styleFrom(
            backgroundColor: const Color(0xff218354),
          ),
          onPressed: !model.ready ? null : () => model.navigate(Screen.receive),
          icon: const Icon(Icons.south_west_rounded),
          label: const Text('Принять файлы'),
        ),
        const SizedBox(height: 20),
        const Divider(),
        const Text(
          'Папка для полученных файлов',
          style: TextStyle(fontWeight: FontWeight.w600),
        ),
        const SizedBox(height: 8),
        SelectableText(
          model.ready ? model.folderLabel : 'Подготовка приложения…',
        ),
        const SizedBox(height: 12),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            OutlinedButton.icon(
              onPressed: !model.ready || actionRunning
                  ? null
                  : () => action(() async {
                      final folder = await model.storage.chooseFolder();
                      if (folder != null) await model.changeFolder(folder);
                    }),
              icon: const Icon(Icons.folder_open_rounded),
              label: const Text('Выбрать папку'),
            ),
            TextButton(
              onPressed: !model.ready || actionRunning
                  ? null
                  : () => action(() => model.changeFolder(null)),
              child: const Text('По умолчанию'),
            ),
          ],
        ),
      ]),
      if (model.recent.isNotEmpty) ...[
        const SizedBox(height: 20),
        card([
          title('Полученные файлы'),
          paragraph(
            '${model.recent.length} файлов доступны для копирования и отправки',
          ),
          fileActions(model.recent),
        ]),
      ],
    ],
  );
  Widget send() => card([
    title('Кому отправить?'),
    paragraph('Выбрано файлов: ${model.selected.length}'),
    ...model.selected
        .take(5)
        .map(
          (f) => ListTile(
            contentPadding: EdgeInsets.zero,
            leading: const Icon(Icons.insert_drive_file_outlined),
            title: Text(f.name, maxLines: 2, overflow: TextOverflow.ellipsis),
          ),
        ),
    if (model.selected.length > 5) Text('И ещё ${model.selected.length - 5}…'),
    TextButton.icon(
      onPressed: actionRunning ? null : () => action(pick),
      icon: const Icon(Icons.add_rounded),
      label: const Text('Изменить выбор'),
    ),
    fileActions(model.selected.map((f) => f.file).toList()),
    const Divider(),
    Row(
      children: [
        const Expanded(
          child: Text(
            'Устройства рядом',
            style: TextStyle(fontSize: 20, fontWeight: FontWeight.w700),
          ),
        ),
        IconButton(
          tooltip: 'Обновить поиск',
          onPressed: () => model.discovery?.refresh(),
          icon: const Icon(Icons.refresh_rounded),
        ),
      ],
    ),
    if (model.discovery?.peers.isEmpty ?? true)
      paragraph(
        'Ищем устройства… У получателя откройте приложение и нажмите «Принять файлы».',
      ),
    ...?model.discovery?.peers.values.map(
      (peer) => ListTile(
        contentPadding: EdgeInsets.zero,
        leading: const Icon(Icons.devices_rounded),
        title: Text(peer.name),
        subtitle: Text('${peer.endpoint}'),
        trailing: const Icon(Icons.send_rounded),
        onTap: () => model.send(peer.endpoint),
      ),
    ),
    if (model.discovery?.warning != null) paragraph(model.discovery!.warning!),
    const SizedBox(height: 16),
    TextField(
      controller: manual,
      decoration: const InputDecoration(
        labelText: 'Адрес:порт',
        hintText: '192.168.1.5:50071',
      ),
      onSubmitted: (value) => action(() async {
        await model.send(Endpoint.parse(value));
      }),
    ),
    const SizedBox(height: 12),
    FilledButton.icon(
      onPressed: actionRunning
          ? null
          : () => action(() async {
              await model.send(Endpoint.parse(manual.text));
            }),
      icon: const Icon(Icons.send_rounded),
      label: const Text('Отправить по адресу'),
    ),
  ]);
  Widget receive() => card([
    const Icon(Icons.radar_rounded, size: 76, color: Color(0xff218354)),
    const SizedBox(height: 20),
    title('Готово к приёму'),
    paragraph(
      'Выберите это устройство у отправителя. Перед сохранением можно будет выбрать другую папку.',
    ),
    Text(
      model.name,
      style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w700),
    ),
    const SizedBox(height: 12),
    ...?model.discovery?.addresses.map(
      (address) => Padding(
        padding: const EdgeInsets.only(bottom: 8),
        child: SelectableText(address),
      ),
    ),
    if (model.discovery?.warning != null) paragraph(model.discovery!.warning!),
    const Divider(),
    paragraph('Сохранение: ${model.folderLabel}'),
  ]);
  Widget transfer() => card([
    if (model.busy)
      Image.asset(
        'assets/transfer_active.gif',
        height: 148,
        gaplessPlayback: true,
        excludeFromSemantics: true,
      )
    else
      Icon(
        model.success
            ? Icons.check_circle_rounded
            : Icons.error_outline_rounded,
        size: 88,
        color: model.success
            ? const Color(0xff218354)
            : const Color(0xffc43d35),
      ),
    const SizedBox(height: 20),
    title(
      model.busy
          ? (model.incoming ? 'Получение файлов' : 'Отправка файлов')
          : model.success
          ? (model.incoming ? 'Приём завершён' : 'Отправка завершена')
          : 'Передача не завершена',
    ),
    if (model.busy) ...[
      LinearProgressIndicator(
        value: model.total > 0 ? (model.done / model.total).clamp(0, 1) : null,
        minHeight: 7,
        borderRadius: BorderRadius.circular(8),
      ),
      const SizedBox(height: 12),
      if (model.total > 0)
        Text(
          '${(model.done * 100 / model.total).floor()}% • ${size(model.done)} / ${size(model.total)}',
        ),
      const SizedBox(height: 12),
    ],
    paragraph(model.status),
    if (model.busy)
      OutlinedButton(onPressed: model.cancel, child: const Text('Отменить')),
    if (!model.busy && model.incoming && model.received.isNotEmpty) ...[
      ...model.received
          .take(5)
          .map(
            (f) => Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: Text(p.basename(f.path)),
            ),
          ),
      const SizedBox(height: 12),
      fileActions(model.received),
    ],
  ]);
  @override
  Widget build(BuildContext context) => PopScope(
    canPop: !model.busy && model.screen == Screen.home,
    onPopInvokedWithResult: (didPop, _) {
      if (!didPop && !model.busy) model.navigate(Screen.home);
    },
    child: Scaffold(
      body: Container(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [Color(0xff12283f), Color(0xff315fa0)],
          ),
        ),
        child: SafeArea(
          child: Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 820),
              child: ListView(
                padding: const EdgeInsets.symmetric(
                  horizontal: 20,
                  vertical: 24,
                ),
                children: [
                  Row(
                    children: [
                      Image.asset(
                        'assets/app-icon.png',
                        width: 44,
                        height: 44,
                        excludeFromSemantics: true,
                      ),
                      const SizedBox(width: 12),
                      const Expanded(
                        child: Text(
                          'TransportationFiles',
                          style: TextStyle(
                            color: Colors.white,
                            fontSize: 23,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                      ),
                      if (model.screen != Screen.home)
                        IconButton(
                          tooltip: 'Главная',
                          onPressed: model.busy
                              ? null
                              : () => model.navigate(Screen.home),
                          icon: const Icon(
                            Icons.home_outlined,
                            color: Colors.white,
                          ),
                        ),
                    ],
                  ),
                  const SizedBox(height: 28),
                  switch (model.screen) {
                    Screen.home => home(),
                    Screen.send => send(),
                    Screen.receive => receive(),
                    Screen.transfer => transfer(),
                  },
                  if (model.notice.isNotEmpty)
                    Padding(
                      padding: const EdgeInsets.only(top: 18),
                      child: Semantics(
                        liveRegion: true,
                        child: Text(
                          model.notice,
                          style: const TextStyle(
                            color: Colors.white,
                            fontSize: 16,
                          ),
                        ),
                      ),
                    ),
                  if (actionRunning)
                    const Padding(
                      padding: EdgeInsets.only(top: 12),
                      child: LinearProgressIndicator(),
                    ),
                  if (model.screen != Screen.home && !model.busy)
                    Padding(
                      padding: const EdgeInsets.only(top: 20),
                      child: TextButton(
                        onPressed: () => model.navigate(Screen.home),
                        child: const Text(
                          'Вернуться в меню',
                          style: TextStyle(color: Colors.white),
                        ),
                      ),
                    ),
                ],
              ),
            ),
          ),
        ),
      ),
    ),
  );
}

String size(int bytes) {
  if (bytes < 1024) return '$bytes Б';
  if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} КБ';
  if (bytes < 1024 * 1024 * 1024) {
    return '${(bytes / 1024 / 1024).toStringAsFixed(1)} МБ';
  }
  return '${(bytes / 1024 / 1024 / 1024).toStringAsFixed(1)} ГБ';
}

class ReceiveDialog extends StatefulWidget {
  final Offer offer;
  final Future<void> cancelled;
  final SystemFiles storage;
  const ReceiveDialog({
    super.key,
    required this.offer,
    required this.cancelled,
    required this.storage,
  });
  @override
  State<ReceiveDialog> createState() => _ReceiveDialogState();
}

class _ReceiveDialogState extends State<ReceiveDialog> {
  String? folder, exactPath, error;
  bool choosing = false;
  @override
  void initState() {
    super.initState();
    widget.cancelled.then((_) {
      if (mounted) Navigator.of(context).pop();
    });
  }

  Future<void> choose(bool exact) async {
    setState(() {
      choosing = true;
    });
    try {
      final value = exact
          ? await FilePicker.saveFile(
              fileName: widget.offer.name,
              dialogTitle: 'Куда сохранить файл?',
            )
          : await widget.storage.chooseFolder();
      if (mounted && value != null) {
        setState(() {
          if (exact) {
            exactPath = value;
            folder = null;
          } else {
            folder = value;
            exactPath = null;
          }
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          error = errorText(e);
        });
      }
    } finally {
      if (mounted) {
        setState(() {
          choosing = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: const Text('Принять файлы?'),
    content: SizedBox(
      width: 440,
      child: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Text('От: ${widget.offer.sender}'),
            const SizedBox(height: 12),
            ...widget.offer.files
                .take(10)
                .map((f) => Text('${f.name} • ${size(f.size)}')),
            if (widget.offer.files.length > 10)
              Text('И ещё ${widget.offer.files.length - 10}…'),
            const SizedBox(height: 16),
            Text('Всего: ${size(widget.offer.size)}'),
            const SizedBox(height: 12),
            Text(
              'Сохранить: ${exactPath ?? (folder == null ? widget.storage.folderLabel : Uri.decodeFull(folder!))}',
            ),
            const SizedBox(height: 12),
            OutlinedButton.icon(
              onPressed: choosing ? null : () => choose(false),
              icon: const Icon(Icons.folder_open),
              label: const Text('Другая папка'),
            ),
            if (!Platform.isAndroid && !widget.offer.multiple)
              TextButton(
                onPressed: choosing ? null : () => choose(true),
                child: const Text('Сохранить как…'),
              ),
            if (error != null) Text(error!),
          ],
        ),
      ),
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.pop(context),
        child: const Text('Отклонить'),
      ),
      FilledButton(
        onPressed: choosing
            ? null
            : () => Navigator.pop(
                context,
                SaveChoice(folder: folder, exactPath: exactPath),
              ),
        child: const Text('Принять'),
      ),
    ],
  );
}

class FileSelection extends StatefulWidget {
  final List<File> files;
  const FileSelection({super.key, required this.files});
  @override
  State<FileSelection> createState() => _FileSelectionState();
}

class _FileSelectionState extends State<FileSelection> {
  late final selected = widget.files.toSet();
  @override
  Widget build(BuildContext context) => AlertDialog(
    title: const Text('Выберите файлы'),
    content: SizedBox(
      width: 460,
      height: 320,
      child: Column(
        children: [
          CheckboxListTile(
            title: const Text('Выбрать все'),
            value: selected.length == widget.files.length,
            onChanged: (value) => setState(() {
              selected.clear();
              if (value == true) selected.addAll(widget.files);
            }),
          ),
          Expanded(
            child: ListView.builder(
              itemCount: widget.files.length,
              itemBuilder: (_, index) {
                final file = widget.files[index];
                return CheckboxListTile(
                  title: Text(p.basename(file.path)),
                  subtitle: Text(
                    p.dirname(file.path),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  value: selected.contains(file),
                  onChanged: (value) => setState(() {
                    if (value == true) {
                      selected.add(file);
                    } else {
                      selected.remove(file);
                    }
                  }),
                );
              },
            ),
          ),
        ],
      ),
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.pop(context),
        child: const Text('Отмена'),
      ),
      FilledButton(
        onPressed: selected.isEmpty
            ? null
            : () => Navigator.pop(
                context,
                widget.files.where(selected.contains).toList(),
              ),
        child: const Text('Выбрать'),
      ),
    ],
  );
}
