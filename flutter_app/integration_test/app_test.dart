import 'dart:async';
import 'dart:io';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:image/image.dart' as raster;
import 'package:integration_test/integration_test.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:super_clipboard/super_clipboard.dart';
import 'package:transportfiles/main.dart' as app;
import 'package:transportfiles/model.dart';
import 'package:transportfiles/transfer/files.dart';
import 'package:transportfiles/transfer/transfer.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  testWidgets(
    'receive through real UI and copy native clipboard representations',
    (tester) async {
      app.main();
      await tester.pumpAndSettle();
      final dynamic state = tester.state(find.byType(app.HomePage));
      final AppModel model = state.model;
      Future<void> until(bool Function() condition) async {
        final deadline = DateTime.now().add(const Duration(seconds: 30));
        while (!condition()) {
          if (DateTime.now().isAfter(deadline)) {
            fail('Timed out: ${model.status} ${model.notice}');
          }
          await tester.pump(const Duration(milliseconds: 100));
        }
      }

      await until(() => model.ready);
      final temporary = await getTemporaryDirectory();
      await temporary.create(recursive: true);
      final root = await temporary.createTemp('native-smoke-');
      final oldFolder = model.storage.folder;
      try {
        if (!Platform.isAndroid) await model.changeFolder(root.path);
        await tester.tap(find.text('Принять файлы'));
        await tester.pumpAndSettle();
        final bytes = List.generate(1048577, (i) => i % 251);
        final source = await File(p.join(root.path, 'source.bin'))
            .writeAsBytes(bytes);
        final sender = TransferSender(Endpoint('127.0.0.1', model.node!.port));
        Object? failure;
        var finished = false;
        final sending = sender
            .send(
              [SourceFile(source, 'native-received.bin')],
              'Native test',
              () {},
              (_, _) {},
            )
            .then(
              (_) {
                finished = true;
              },
              onError: (Object e) {
                failure = e;
                finished = true;
              },
            );
        await until(() => find.text('Принять файлы?').evaluate().isNotEmpty);
        await tester.tap(find.text('Принять'));
        await tester.pump();
        await until(() => finished && !model.busy);
        await sending;
        await sender.close();
        expect(failure, isNull);
        expect(model.success, isTrue, reason: model.status);
        expect(await model.received.single.readAsBytes(), bytes);
        expect(find.text('Приём завершён'), findsOneWidget);
        debugPrint('Native smoke: received file verified; copying PNG');

        final picture = raster.Image(width: 2, height: 2)
          ..setPixelRgb(0, 0, 255, 0, 0);
        final photo = await File(p.join(root.path, 'photo.png'))
            .writeAsBytes(raster.encodePng(picture));
        await model.storage.copy([photo]);
        final clipboard = SystemClipboard.instance!;
        final imageReader = await clipboard.read();
        expect(
          imageReader.canProvide(Formats.png),
          isTrue,
          reason:
              'Items: ${imageReader.items.length}; formats: ${imageReader.getFormats(Formats.standardFormats)}',
        );
        final imageBytes = Completer<List<int>>();
        imageReader.getFile(Formats.png, (file) async {
          try {
            imageBytes.complete(
              await file.getStream().expand((bytes) => bytes).toList(),
            );
          } catch (e) {
            imageBytes.completeError(e);
          }
        }, onError: imageBytes.completeError);
        final decoded = raster.decodePng(
          Uint8List.fromList(
            await imageBytes.future.timeout(const Duration(seconds: 10)),
          ),
        );
        expect(decoded?.width, 2);
        expect(decoded?.height, 2);
        expect(decoded?.getPixel(0, 0).r, 255);
        debugPrint('Native smoke: PNG pixels verified; copying file list');

        await model.storage.copy([source, photo]);
        final filesReader = await clipboard.read();
        expect(filesReader.items.length, 2);
        if (!Platform.isAndroid) {
          final values = await Future.wait(
            filesReader.items.map((item) => item.readValue(Formats.fileUri)),
          );
          expect(values.toSet(), {source.uri, photo.uri});
        }
        debugPrint('Native smoke: file list verified');
        // This runs only in a disposable CI runner / isolated Android test app.
        await clipboard.write([
          DataWriterItem()
            ..add(Formats.plainText('TransportationFiles native smoke passed')),
        ]);
        if (Platform.isAndroid) {
          for (final file in model.received) {
            await file.delete();
          }
        }
      } finally {
        if (!Platform.isAndroid) await model.changeFolder(oldFolder);
        await tester.pumpWidget(const SizedBox());
        await root.delete(recursive: true);
      }
    },
    timeout: const Timeout(Duration(minutes: 2)),
  );
}
