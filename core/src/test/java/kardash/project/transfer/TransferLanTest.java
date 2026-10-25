package kardash.project.transfer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import java.net.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "TRANSPORT_DISCOVERY_TEST", matches = "true")
class TransferLanTest {
    @TempDir Path directory;

    @Test void transfersThroughOwnLanAddress() throws Exception {
        List<String> hosts = new ArrayList<>(List.of("127.0.0.1"));
        for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!network.isUp() || network.isLoopback() || network.isPointToPoint()) continue;
            for (InterfaceAddress address : network.getInterfaceAddresses()) {
                if (address.getAddress() instanceof Inet4Address && address.getBroadcast() != null) {
                    hosts.add(address.getAddress().getHostAddress());
                }
            }
        }
        assertTrue(hosts.size() > 1, "A LAN interface is required");
        Path source = Files.write(directory.resolve("source"), new byte[]{1, 2, 3});
        for (String host : hosts) {
            Path target = directory.resolve("received-" + host);
            CompletableFuture<Path> received = new CompletableFuture<>();
            try (TransferNode node = new TransferNode(0, new TransferNode.Listener() {
                @Override public CompletableFuture<Path> accept(TransferNode.Offer offer) {
                    return CompletableFuture.completedFuture(target);
                }
                @Override public void received(Path file) { received.complete(file); }
                @Override public void failed(String message) { received.completeExceptionally(new AssertionError(message)); }
            }); TransferClient client = new TransferClient(host, node.port())) {
                assertTimeoutPreemptively(Duration.ofSeconds(15), () -> {
                    client.send(source, "source", "LAN test", () -> {}, (a, b) -> {});
                    assertEquals(target, received.get());
                }, "Transfer through " + host);
                assertArrayEquals(Files.readAllBytes(source), Files.readAllBytes(target));
            }
        }
    }
}
