package org.example.cotrollers;

import org.example.constants.Constants;

import java.io.IOException;
import java.net.*;
import java.util.Enumeration;
import java.util.Map;

public class MulticastController {

    private final String MULTICAST_GROUP = "224.0.0.1";
    private final int MULTICAST_PORT = 4445;

    private final Map<InetAddress, String> users;

    public MulticastController(Map<InetAddress, String> users) {
        this.users = users;
    }

    public void startDiscovery() throws IOException {

        InetAddress group = InetAddress.getByName(MULTICAST_GROUP);
        MulticastSocket socket = new MulticastSocket(MULTICAST_PORT);
        socket.joinGroup(group);

        String localIP =  getLocalNetworkIP();

        // Поток для приёма сообщений
        new Thread(() -> {
            byte[] buf = new byte[1024];
            while (true) {
                try {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    // Игнорируем свои же сообщения и loopback
                    InetAddress address = packet.getAddress();
//                    if (!address.getHostAddress().equals(localIP)) {
                        String message = new String(packet.getData(), 0, packet.getLength());
                        System.out.println("Discovered device: " + address.getCanonicalHostName() + " (" +  address.getHostAddress() + ")");
                        users.put(packet.getAddress(), address.getCanonicalHostName());
//                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }).start();

        // Поток для отправки сообщений
        new Thread(() -> {
            while (true) {
                try {
                    String message = "HELLO: " + localIP + ": " + Constants.GRPC_PORT;
                    byte[] buf = message.getBytes();
                    socket.send(new DatagramPacket(buf, buf.length, group, MULTICAST_PORT));
                    Thread.sleep(5000);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();

    }

    private String getLocalNetworkIP() throws SocketException {
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
