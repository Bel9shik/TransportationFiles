package kardash.project.transfer;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DiscoveryAnnouncementTest {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void alsoSendsLanAnnouncementWhenMulticastSucceedsOrFails(boolean multicastFails) throws Exception {
        byte[] payload = "TF1\nother\n51002\nReceiver".getBytes(StandardCharsets.UTF_8);
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        // Simulate a VPN which drops multicast without reporting an error, or rejects it explicitly.
        try (MulticastSocket multicast = new MulticastSocket(null) {
            @Override public void setNetworkInterface(NetworkInterface network) {}
            @Override public void send(DatagramPacket packet) throws IOException {
                if (multicastFails) throw new NoRouteToHostException("VPN multicast route");
            }
        }; DatagramSocket receiver = new DatagramSocket(new InetSocketAddress(loopback, 4445));
             DatagramSocket broadcaster = new DatagramSocket(new InetSocketAddress(loopback, 0))) {
            receiver.setSoTimeout(2000);
            assertTrue(Discovery.announce(multicast, InetAddress.getByName("239.255.44.45"),
                    List.of(NetworkInterface.getByInetAddress(loopback)),
                    List.of(new Discovery.Broadcast(broadcaster, loopback)), payload));
            DatagramPacket packet = new DatagramPacket(new byte[1024], 1024);
            receiver.receive(packet);
            Discovery.Peer found = Discovery.decode(new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8),
                    packet.getAddress().getHostAddress(), "self");
            assertEquals(new Discovery.Peer("127.0.0.1", 51002, "Receiver"), found);
        }
    }
}
