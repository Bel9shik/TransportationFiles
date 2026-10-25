package kardash.project.transfer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "TRANSPORT_DISCOVERY_TEST", matches = "true")
class DiscoveryNetworkTest {
    @Test void twoParticipantsFindEachOtherWithoutManualAddress() throws Exception {
        CompletableFuture<Discovery.Peer> senderFound = new CompletableFuture<>();
        CompletableFuture<Discovery.Peer> receiverFound = new CompletableFuture<>();
        try (Discovery sender = new Discovery(51001, "Test sender", peers -> peers.stream()
                .filter(peer -> peer.port() == 51002).findFirst().ifPresent(receiverFound::complete),
                message -> receiverFound.completeExceptionally(new AssertionError(message)));
             Discovery receiver = new Discovery(51002, "Test receiver", peers -> peers.stream()
                .filter(peer -> peer.port() == 51001).findFirst().ifPresent(senderFound::complete),
                message -> senderFound.completeExceptionally(new AssertionError(message)))) {
            assertEquals(51002, receiverFound.get(8, TimeUnit.SECONDS).port());
            assertEquals(51001, senderFound.get(8, TimeUnit.SECONDS).port());
        }
    }
}
