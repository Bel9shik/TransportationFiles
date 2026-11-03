import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:image/image.dart' as raster;
import 'package:transportfiles/system_files.dart';

void main() {
  test(
    'clipboard conversion preserves pixels without Flutter GPU decoding',
    () async {
      final root = await Directory.systemTemp.createTemp('clipboard-image-');
      try {
      final picture = raster.Image(width: 2, height: 3)
        ..setPixelRgba(0, 0, 255, 0, 0, 255);
      final gif = raster.Image(width: 2, height: 3, withPalette: true);
      gif.palette!.setRgb(0, 255, 0, 0);
        final formats = {
          'png': raster.encodePng(picture),
          'bmp': raster.encodeBmp(picture),
          'tiff': raster.encodeTiff(picture),
        'gif': raster.encodeGif(gif),
        };
        for (final entry in formats.entries) {
          final file = await File('${root.path}/image.${entry.key}')
              .writeAsBytes(entry.value);
          final result = raster.decodePng(await clipboardImage(file))!;
          expect((result.width, result.height), (2, 3), reason: entry.key);
          expect(result.getPixel(0, 0).r, 255, reason: entry.key);
          expect(result.getPixel(0, 0).g, 0, reason: entry.key);
        }
        picture.exif.imageIfd.orientation = 6;
        final rotated = await File('${root.path}/rotated.jpg')
            .writeAsBytes(raster.encodeJpg(picture));
        final result = raster.decodePng(await clipboardImage(rotated))!;
        expect((result.width, result.height), (3, 2));
        final broken = await File('${root.path}/broken.png')
            .writeAsString('not an image');
        await expectLater(clipboardImage(broken), throwsFormatException);
      } finally {
        await root.delete(recursive: true);
      }
    },
  );
}
