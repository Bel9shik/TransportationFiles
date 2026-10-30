import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:transportfiles/main.dart';
import 'package:transportfiles/model.dart';
import 'package:transportfiles/system_files.dart';
import 'package:transportfiles/transfer/files.dart';

class PreviewModel extends AppModel {
  PreviewModel()
    : super(
        SystemFiles()..downloads = '/Users/me/Downloads',
        (_, _) async => null,
      ) {
    ready = true;
    folderLabel = 'Загрузки / TransportationFiles';
  }
  @override
  Future<void> start() async {}
}

void main() {
  for (final dimensions in [const Size(390, 844), const Size(900, 760)]) {
    for (final screen in Screen.values) {
      testWidgets('${screen.name} fits ${dimensions.width}', (tester) async {
        tester.view.physicalSize = dimensions;
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        final sdk = Platform.environment['FLUTTER_ROOT'];
        await tester.runAsync(() async {
          if (sdk != null) {
            final font = File(
              '$sdk/bin/cache/artifacts/material_fonts/Roboto-Regular.ttf',
            );
            if (await font.exists()) {
              await (FontLoader('Roboto')..addFont(
                    font.readAsBytes().then((b) => ByteData.sublistView(b)),
                  ))
                  .load();
            }
            final icons = File(
              '$sdk/bin/cache/artifacts/material_fonts/MaterialIcons-Regular.otf',
            );
            await (FontLoader('MaterialIcons')..addFont(
                  icons.readAsBytes().then((b) => ByteData.sublistView(b)),
                ))
                .load();
          }
        });
        final model = PreviewModel()..screen = screen;
        model.selected = [
          SourceFile(File('/photos/Москва.jpg')),
          SourceFile(File('/files/Документы.pdf')),
        ];
        if (screen == Screen.transfer) {
          model.busy = true;
          model.total = 10485760;
          model.done = 6500000;
          model.status = 'Отправка файлов…';
        }
        final key = GlobalKey();
        await tester.pumpWidget(
          RepaintBoundary(
            key: key,
            child: TransportApp(controller: model),
          ),
        );
        if (Platform.environment['TRANSPORT_SCREENSHOTS'] == 'true') {
          for (var frame = 0; frame < 5; frame++) {
            await tester.runAsync(
              () => Future<void>.delayed(const Duration(milliseconds: 100)),
            );
            await tester.pump(const Duration(milliseconds: 16));
          }
        } else {
          await tester.pump(const Duration(milliseconds: 100));
        }
        expect(tester.takeException(), isNull);
        if (Platform.environment['TRANSPORT_SCREENSHOTS'] == 'true') {
          await tester.runAsync(() async {
            final image =
                await (key.currentContext!.findRenderObject()!
                        as RenderRepaintBoundary)
                    .toImage();
            try {
              final png = await image.toByteData(
                format: ui.ImageByteFormat.png,
              );
              await Directory('build/screenshots').create(recursive: true);
              await File(
                'build/screenshots/${screen.name}-${dimensions.width.toInt()}.png',
              ).writeAsBytes(png!.buffer.asUint8List());
            } finally {
              image.dispose();
            }
          });
        }
        await tester.pumpWidget(const SizedBox());
      });
    }
  }
}
