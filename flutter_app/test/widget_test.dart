import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:transportfiles/main.dart';
import 'package:transportfiles/system_files.dart';
import 'package:transportfiles/transfer/transfer.dart';

void main() {
  testWidgets('file selection supports subset and select all on a phone', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final files = [File('/photos/a.png'), File('/photos/b.png')];
    List<File>? result;
    await tester.pumpWidget(
      MaterialApp(
        home: Builder(
          builder: (context) => Scaffold(
            body: TextButton(
              onPressed: () async {
                result = await showDialog<List<File>>(
                  context: context,
                  builder: (_) => FileSelection(files: files),
                );
              },
              child: const Text('Open'),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('Open'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('a.png'));
    await tester.tap(find.text('Выбрать'));
    await tester.pumpAndSettle();
    expect(result, [files[1]]);
    expect(tester.takeException(), isNull);
  });
  testWidgets('incoming dialog closes when sender cancels', (tester) async {
    final cancelled = Completer<void>();
    final storage = SystemFiles()..downloads = '/Downloads';
    await tester.pumpWidget(
      MaterialApp(
        home: Builder(
          builder: (context) => Scaffold(
            body: TextButton(
              onPressed: () => showDialog<void>(
                context: context,
                builder: (_) => ReceiveDialog(
                  offer: const Offer('Mac', 'file.png', [
                    OfferedFile('file.png', 12),
                  ], 12),
                  cancelled: cancelled.future,
                  storage: storage,
                ),
              ),
              child: const Text('Open'),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('Open'));
    await tester.pumpAndSettle();
    expect(find.text('Принять файлы?'), findsOneWidget);
    cancelled.complete();
    await tester.pumpAndSettle();
    expect(find.text('Принять файлы?'), findsNothing);
  });
}
