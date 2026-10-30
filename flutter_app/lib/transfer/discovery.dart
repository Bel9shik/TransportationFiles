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
  final _senders = <(RawDatagramSocket, InternetAddress, InterfaceAddress)>[];
  final _answered = <String, DateTime>{};
  RawDatagramSocket? _receiver;
  Timer? _timer;
  bool _refreshing = false, _closed = false;
  String? _signature, warning;
  Discovery(this.serverPort, this.name, this.changed);

  Future<void> start() async {
    await refresh();
    if (!_closed) {
      _timer = Timer.periodic(const Duration(seconds: 3), (_) => refresh());
    }
  }

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
          final lastAnswer = _answered[peer.id];
          if (answer &&
              (lastAnswer == null ||
                  DateTime.now().difference(lastAnswer) >=
                      const Duration(seconds: 3))) {
            _answered[peer.id] = DateTime.now();
            // A failed send closes a Dart UDP socket. Keep replies off the
            // shared receiver and bind their source to the peer's LAN subnet.
            for (final (sender, destination, address) in _senders) {
              final shift = 32 - address.prefixLength;
              int ipv4(InternetAddress value) =>
                  value.rawAddress.fold(0, (n, b) => (n << 8) | b);
              if (destination == group ||
                  ipv4(address) >> shift != ipv4(packet.address) >> shift) {
                continue;
              }
              try {
                sender.send(_announcement, packet.address, packet.port);
              } on SocketException {
                /* Periodic advertisements can recover. */
              }
              break;
            }
          }
          changed();
        }
      },
      onError: (Object error) {
        if (_closed) return;
        if (identical(_receiver, socket)) _receiver = null;
        _senders.removeWhere((entry) => identical(entry.$1, socket));
        _signature = null;
        if (_receiver == null || _senders.isEmpty) {
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
      if (_receiver == null) {
        final socket = await RawDatagramSocket.bind(
          InternetAddress.anyIPv4,
          port,
          reuseAddress: true,
          reusePort: Platform.isMacOS || Platform.isLinux,
        );
        if (_closed) {
          socket.close();
          return;
        }
        _receiver = socket;
        _listen(socket, answer: true);
        _signature = null;
      }
      final interfaces = await NetworkInterface.list(
        type: InternetAddressType.IPv4,
      );
      final candidates = <(NetworkInterface, InterfaceAddress)>[
        for (final nic in interfaces)
          if (!RegExp(
            r'^(utun|tun|tap|ppp|wg|tailscale)',
            caseSensitive: false,
          ).hasMatch(nic.name))
            for (final address in nic.addresses)
              if (address.broadcast != null && !address.isLoopback)
                (nic, address),
      ];
      final signature = candidates
          .map(
            (e) =>
                '${e.$1.index}/${e.$2.address}/${e.$2.prefixLength}/${e.$2.broadcast}',
          )
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
          // Multicast route errors must not disable directed broadcast.
          for (final destination in [address.broadcast!, group]) {
            RawDatagramSocket? socket;
            try {
              socket = await RawDatagramSocket.bind(address, 0);
              if (_closed) {
                socket.close();
                return;
              }
              if (destination == group) {
                socket.multicastHops = 1;
                socket.setRawOption(
                  RawSocketOption(
                    RawSocketOption.levelIPv4,
                    RawSocketOption.IPv4MulticastInterface,
                    address.rawAddress,
                  ),
                );
              } else {
                socket.broadcastEnabled = true;
              }
              _listen(socket, answer: false);
              _senders.add((socket, destination, address));
            } on SocketException {
              socket?.close();
            }
          }
        }
        _signature = signature;
      }
      final message = _announcement;
      for (final (socket, destination, _) in _senders) {
        try {
          socket.send(message, destination, port);
        } on SocketException {
          _signature = null;
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
