import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:transportfiles/transfer/discovery.dart';

void main() {
  test('two LAN peers discover each other through real UDP sockets', () async {
    final first = Discovery(50071, 'First', () {}),
        second = Discovery(50072, 'Second', () {});
    try {
      await first.start();
      await second.start();
      expect(first.warning, isNull);
      expect(second.warning, isNull);
      expect(first.addresses, isNotEmpty, reason: first.warning);
      expect(second.addresses, isNotEmpty, reason: second.warning);
      for (var i = 0; i < 50; i++) {
        await first.refresh();
        await second.refresh();
        if (first.peers.containsKey(second.id) &&
            second.peers.containsKey(first.id)) {
          break;
        }
        await Future<void>.delayed(const Duration(milliseconds: 100));
      }
      expect(first.peers[second.id]?.endpoint.port, 50072);
      expect(second.peers[first.id]?.endpoint.port, 50071);
    } finally {
      first.close();
      second.close();
    }
  }, skip: Platform.environment['TRANSPORT_DISCOVERY_TEST'] != 'true');
}
