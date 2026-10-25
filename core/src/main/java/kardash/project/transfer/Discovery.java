package kardash.project.transfer;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** LAN discovery is optional; transfers also work with a manually entered address. */
public final class Discovery implements AutoCloseable {
    public record Peer(String host, int port, String name) {
        @Override public String toString() { return name + " — " + host + ":" + port; }
    }
    private record Seen(Peer peer, long at) {}
    record Broadcast(DatagramSocket socket, InetAddress address) {}
    private static final int PORT = 4445;
    private final String id = UUID.randomUUID().toString();
    private final Map<String, Seen> peers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService threads = Executors.newScheduledThreadPool(2);
    private final MulticastSocket socket;
    private final InetAddress group;
    private final List<NetworkInterface> interfaces = new ArrayList<>();
    private final List<Broadcast> broadcasts = new ArrayList<>();
    private final Consumer<List<Peer>> updated;
    private final Consumer<String> failed;
    private volatile boolean closed;

    public Discovery(int port, String name, Consumer<List<Peer>> updated, Consumer<String> failed) throws IOException {
        this.updated = updated;
        this.failed = failed;
        group = InetAddress.getByName("239.255.44.45");
        socket = new MulticastSocket(null);
        try {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(PORT));
            socket.setTimeToLive(1);
            socket.setSoTimeout(1000);
            for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!network.isUp() || network.isLoopback() || network.isPointToPoint()) continue;
                if (Collections.list(network.getInetAddresses()).stream().noneMatch(a -> a instanceof Inet4Address)) continue;
                if (network.supportsMulticast()) {
                    try {
                        socket.joinGroup(new InetSocketAddress(group, PORT), network);
                        interfaces.add(network);
                    } catch (IOException ignored) { /* Broadcast can still reach this LAN. */ }
                }
                for (InterfaceAddress address : network.getInterfaceAddresses()) {
                    if (!(address.getAddress() instanceof Inet4Address) || address.getBroadcast() == null) continue;
                    DatagramSocket sender = new DatagramSocket(null);
                    try {
                        sender.setBroadcast(true);
                        // Bind the source to the LAN interface instead of relying on the VPN default route.
                        sender.bind(new InetSocketAddress(address.getAddress(), 0));
                        broadcasts.add(new Broadcast(sender, address.getBroadcast()));
                    } catch (IOException e) { sender.close(); }
                }
            }
            if (interfaces.isEmpty() && broadcasts.isEmpty()) {
                throw new IOException("Нет доступного LAN-интерфейса для автопоиска");
            }
            byte[] announcement = ("TF1\n" + id + "\n" + port + "\n" + name.replace('\n', ' ')).getBytes(StandardCharsets.UTF_8);
            threads.execute(this::receive);
            threads.scheduleWithFixedDelay(() -> {
                if (!announce(socket, group, interfaces, broadcasts, announcement) && !closed) {
                    failed.accept("Не удалось отправить запрос поиска. Проверьте доступ к LAN в VPN и firewall.");
                }
                if (peers.values().removeIf(peer -> System.nanoTime() - peer.at() > TimeUnit.SECONDS.toNanos(15))) publish();
            }, 0, 3, TimeUnit.SECONDS);
        } catch (IOException | RuntimeException e) {
            close();
            throw e;
        }
    }

    static boolean announce(MulticastSocket socket, InetAddress group, List<NetworkInterface> interfaces,
                            List<Broadcast> broadcasts, byte[] announcement) {
        boolean sent = false;
        for (NetworkInterface network : interfaces) {
            try {
                socket.setNetworkInterface(network);
                socket.send(new DatagramPacket(announcement, announcement.length, group, PORT));
                sent = true;
            } catch (IOException ignored) { /* Also try directed LAN broadcasts below. */ }
        }
        // Multicast can be silently filtered, so broadcast is sent even when multicast send succeeds.
        for (Broadcast target : broadcasts) {
            try {
                target.socket().send(new DatagramPacket(announcement, announcement.length, target.address(), PORT));
                sent = true;
            } catch (IOException ignored) { /* Report failure if no interface can send. */ }
        }
        return sent;
    }

    static Peer decode(String message, String source, String ownId) {
        String[] parts = message.split("\n", 4);
        if (parts.length != 4 || !parts[0].equals("TF1") || parts[1].equals(ownId)
                || parts[1].length() > 64 || parts[3].trim().isEmpty() || parts[3].length() > 200) return null;
        try {
            int port = Integer.parseInt(parts[2]);
            if (port < 1 || port > 65535) return null;
            return new Peer(source, port, parts[3]);
        } catch (NumberFormatException e) { return null; }
    }

    private void receive() {
        while (!closed) {
            try {
                byte[] bytes = new byte[1024];
                DatagramPacket packet = new DatagramPacket(bytes, bytes.length);
                socket.receive(packet);
                Peer peer = decode(new String(bytes, 0, packet.getLength(), StandardCharsets.UTF_8),
                        packet.getAddress().getHostAddress(), id);
                if (peer != null) {
                    // Bound memory even on a noisy/untrusted LAN.
                    String key = peer.host() + ":" + peer.port();
                    if (peers.size() < 256 || peers.containsKey(key)) {
                        peers.put(key, new Seen(peer, System.nanoTime()));
                        publish();
                    }
                }
            } catch (SocketTimeoutException ignored) {
            } catch (IOException e) {
                if (!closed) failed.accept("Автопоиск остановлен: " + e.getMessage());
                close();
            }
        }
    }

    private void publish() {
        List<Peer> snapshot = new ArrayList<>();
        peers.values().forEach(value -> snapshot.add(value.peer()));
        snapshot.sort(Comparator.comparing(Peer::name));
        updated.accept(snapshot);
    }

    public static String addresses() {
        List<String> addresses = new ArrayList<>();
        try {
            for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!network.isUp() || network.isLoopback()) continue;
                for (InetAddress address : Collections.list(network.getInetAddresses())) {
                    if (address instanceof Inet4Address) addresses.add(address.getHostAddress());
                }
            }
        } catch (SocketException ignored) { }
        return addresses.isEmpty() ? "127.0.0.1" : String.join(", ", addresses);
    }

    public static Peer endpoint(String address) {
        String value = address.trim();
        int separator = value.lastIndexOf(':');
        if (separator < 1) throw new IllegalArgumentException("Укажите адрес в формате IP:порт");
        String host = value.substring(0, separator);
        if (host.startsWith("[") && host.endsWith("]")) host = host.substring(1, host.length() - 1);
        int port;
        try { port = Integer.parseInt(value.substring(separator + 1)); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Некорректный порт", e); }
        if (host.trim().isEmpty() || port < 1 || port > 65535) throw new IllegalArgumentException("Некорректный адрес или порт");
        return new Peer(host, port, host);
    }

    @Override public void close() {
        closed = true;
        socket.close();
        broadcasts.forEach(target -> target.socket().close());
        threads.shutdownNow();
    }
}
