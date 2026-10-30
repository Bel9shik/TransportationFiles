import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'files.dart';

class Peer {
  final String id, name;
  final Endpoint endpoint;
  final DateTime seen;
  Peer(this.id, this.name, this.endpoint, this.seen);
}

Peer? parseAnnouncement(List<int> bytes, InternetAddress source, String ownId) {
  if (bytes.length > 1024) return null;
  try {
    final fields = utf8.decode(bytes).split('\n');
    if (fields.length != 4 ||
        fields[0] != 'TF1' ||
        fields[1].isEmpty ||
        fields[1].length > 64 ||
        fields[1] == ownId ||
        fields[3].isEmpty ||
        fields[3].length > 200) {
      return null;
    }
    final port = int.tryParse(fields[2]);
    if (port == null || port < 1 || port > 65535 || source.isLoopback) {
      return null;
    }
    return Peer(
      fields[1],
      fields[3],
      Endpoint(source.address, port),
      DateTime.now(),
    );
  } on FormatException {
    return null;
  }
}

class Discovery {
  static const port = 4445;
  static final group = InternetAddress('239.255.44.45');
  final int serverPort;
  final String name;
  final String id = randomId();
  final void Function() changed;
  final peers = <String, Peer>{};
  final addresses = <String>[];
  final _senders = <(RawDatagramSocket, InternetAddress)>[];
  RawDatagramSocket? _receiver;
  Timer? _timer;
  bool _refreshing = false, _closed = false;
  String? warning;
  Discovery(this.serverPort, this.name, this.changed);

  Future<void> start() async {
    try {
      _receiver = await RawDatagramSocket.bind(
        InternetAddress.anyIPv4,
        port,
        reuseAddress: true,
        reusePort: Platform.isMacOS || Platform.isLinux,
      );
      _listen(_receiver!, answer: true);
      await refresh();
      if (_closed) return;
      _timer = Timer.periodic(const Duration(seconds: 3), (_) => refresh());
    } on SocketException catch (e) {
      warning = 'Автопоиск недоступен: ${e.message}';
      changed();
    }
  }

  final _answered = <String, DateTime>{};
  List<int> get _announcement => utf8.encode(
    'TF1\n$id\n$serverPort\n${name.replaceAll('\n', ' ').substring(0, name.length > 200 ? 200 : name.length)}',
  );

  void _listen(RawDatagramSocket socket, {required bool answer}) {
    socket.listen(
      (event) {
        if (_closed || event != RawSocketEvent.read) return;
        Datagram? packet;
        while ((packet = socket.receive()) != null) {
          final peer = parseAnnouncement(packet!.data, packet.address, id);
          if (peer == null ||
              (peers.length >= 256 && !peers.containsKey(peer.id))) {
            continue;
          }
          peers[peer.id] = peer;
          // Reply to the observed UDP endpoint, not to the advertised TCP port.
          // Listening on the bound sender sockets also handles asymmetric multicast
          // delivery and multiple receivers sharing port 4445 on macOS.
          final lastAnswer = _answered[peer.id];
          if (answer &&
              (lastAnswer == null ||
                  DateTime.now().difference(lastAnswer) >=
                      const Duration(seconds: 3))) {
            _answered[peer.id] = DateTime.now();
            try {
              socket.send(_announcement, packet.address, packet.port);
            } on SocketException {
              /* Periodic multicast/broadcast remains available. */
            }
          }
          changed();
        }
      },
      onError: (Object error) {
        if (!_closed) {
          warning = 'Автопоиск: $error';
          changed();
        }
      },
    );
  }

  Future<void> refresh() async {
    if (_closed || _refreshing) return;
    _refreshing = true;
    try {
      final interfaces = await NetworkInterface.list(
        type: InternetAddressType.IPv4,
      );
      final candidates = <(NetworkInterface, InterfaceAddress)>[
        for (final nic in interfaces)
          // Point-to-point tunnels have no broadcast address. Skip common VPN
          // names too; advertisements must use the physical LAN source IP.
          if (!RegExp(
            r'^(utun|tun|tap|ppp|wg|tailscale)',
            caseSensitive: false,
          ).hasMatch(nic.name))
            for (final address in nic.addresses)
              if (address.broadcast != null && !address.isLoopback)
                (nic, address),
      ];
      final signature = candidates
          .map((e) => '${e.$1.index}/${e.$2.address}/${e.$2.broadcast}')
          .join(',');
      if (signature != _signature) {
        for (final sender in _senders) {
          sender.$1.close();
        }
        _senders.clear();
        addresses.clear();
        for (final (nic, address) in candidates) {
          addresses.add('${address.address}:$serverPort');
          try {
            _receiver?.joinMulticast(group, nic);
          } on SocketException {
            /* May already be joined. */
          }
          try {
            final socket = await RawDatagramSocket.bind(address, 0);
            if (_closed) {
              socket.close();
              break;
            }
            socket.broadcastEnabled = true;
            socket.multicastHops = 1;
            try {
              socket.setRawOption(
                RawSocketOption(
                  RawSocketOption.levelIPv4,
                  RawSocketOption.IPv4MulticastInterface,
                  address.rawAddress,
                ),
              );
            } on SocketException {
              /* Broadcast is still usable if multicast fails. */
            }
            _listen(socket, answer: false);
            _senders.add((socket, address.broadcast!));
          } on SocketException {
            /* Other interfaces may remain usable. */
          }
        }
        _signature = signature;
      }
      final message = _announcement;
      for (final (socket, broadcast) in _senders) {
        for (final destination in [group, broadcast]) {
          try {
            socket.send(message, destination, port);
          } on SocketException {
            /* VPN can block a route independently. */
          }
        }
      }
      warning = _senders.isEmpty
          ? 'Нет доступного локального IPv4-интерфейса. Можно указать адрес вручную.'
          : null;
      peers.removeWhere(
        (_, peer) =>
            DateTime.now().difference(peer.seen) > const Duration(seconds: 15),
      );
      _answered.removeWhere((key, _) => !peers.containsKey(key));
      changed();
    } on SocketException catch (e) {
      warning = 'Автопоиск: ${e.message}';
      changed();
    } finally {
      _refreshing = false;
    }
  }

  String? _signature;

  void close() {
    _closed = true;
    _timer?.cancel();
    _receiver?.close();
    _receiver = null;
    for (final sender in _senders) {
      sender.$1.close();
    }
    _senders.clear();
  }
}
