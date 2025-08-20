package kardash.project.app.cotrollers;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import kardash.project.app.constants.Constants;
import kardash.project.app.models.User;
import lombok.Getter;

import java.io.IOException;
import java.net.*;
import java.util.Enumeration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class UserDiscoveryService extends Service<ObservableList<User>> {

    @Getter
    private final ObservableList<User> users;

    private MulticastSocket socket;
    private InetAddress groupAddress;
    private ScheduledExecutorService scheduler;
    private volatile boolean running = false;

    public UserDiscoveryService() {
        this.users = FXCollections.observableArrayList();
    }

    @Override
    protected Task<ObservableList<User>> createTask() { // TODO: продумать то, что пользователь может уже стать сопряжённым с кем-то, значит его не нужно показывать в списке
        return new Task<ObservableList<User>>() {

            @Override
            protected ObservableList<User> call() {
                running = true;
                initializeNetwork();

                scheduler = Executors.newSingleThreadScheduledExecutor();
                scheduler.scheduleAtFixedRate(
                        this::sendDiscoveryPacket,
                        0,
                        Constants.DISCOVERY_INTERVAL_MS,
                        TimeUnit.MILLISECONDS
                );

                while (running && !isCancelled()) {
                    receiveDiscoveryPackets();
                }

                return users;
            }

            // Фукнция для приёма сообщений
            private void receiveDiscoveryPackets() {

                byte[] buf = new byte[1024];
                try {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    InetAddress address = packet.getAddress();
                    // Игнорируем свои же сообщения и loopback
                    String message = new String(packet.getData(), 0, packet.getLength());
//                    if (!address.getHostAddress().equals(getLocalNetworkIP()) && message.startsWith("DISCOVERY: ")) processDiscoveryPackets(message, address);
                    processDiscoveryPackets(message, address);
                } catch (SocketTimeoutException | SocketException e) {
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }

            private void processDiscoveryPackets(String message, InetAddress address) {

                System.out.println(message);

                String[] parts = message.split(":");

                if (parts.length == 3) {
                    String ip = parts[1];
                    int port = Integer.parseInt(parts[2]);
                    String hostName = address.getHostName();

                    User user = new User(ip, port, hostName);

                    users.removeIf(u -> u.equals(user));
                    users.add(user);
                    System.out.println("add new user. ip: " + ip + " port: " + port + " host: " + hostName);
                    updateValue(users);
                }
            }

            // Функция для отправки пакетов
            private void sendDiscoveryPacket() {

                try {
                    String localIP = getLocalNetworkIP();
                    String message = "DISCOVERY: " + localIP + ":" + Constants.GRPC_PORT;
                    byte[] buf = message.getBytes();
                    DatagramPacket packet = new DatagramPacket(buf, buf.length, groupAddress, Constants.MULTICAST_PORT);
                    socket.send(packet);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
    }

    private void initializeNetwork() {

        try {
            groupAddress = InetAddress.getByName(Constants.MULTICAST_GROUP);
            socket = new MulticastSocket(Constants.MULTICAST_PORT);
            socket.joinGroup(groupAddress);
            socket.setSoTimeout(1000); // Таймаут для проверки прерывания

        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to initialize multicast", e);
        }

    }

    @Override
    protected void cancelled() {
        cleanup();
        super.cancelled();
    }

    @Override
    protected void failed() {
        cleanup();
        super.failed();
    }

    @Override
    protected void succeeded() {
        cleanup();
        super.succeeded();
    }

    private void cleanup() {
        System.out.println("UserDiscoveryService cleanup");
        running = false;

        if (scheduler != null) scheduler.shutdownNow();

        if (socket != null && !socket.isClosed()) {
            try {
                socket.leaveGroup(groupAddress);
                socket.close();
            } catch (IOException e) {
            }
        }

    }

    public static String getLocalNetworkIP() throws SocketException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface iface = interfaces.nextElement();
            if (iface.isLoopback() || !iface.isUp()) continue;

            Enumeration<InetAddress> addresses = iface.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress addr = addresses.nextElement();
                if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                    return addr.getHostAddress();
                }
            }
        }
        throw new RuntimeException("No network IP found");
    }

}
