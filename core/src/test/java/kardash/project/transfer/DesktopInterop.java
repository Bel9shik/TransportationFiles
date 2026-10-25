package kardash.project.transfer;

import java.nio.file.*;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.*;

/** Counterpart of the opt-in Android → desktop → Android integration test. */
public final class DesktopInterop {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("transport-interop-");
        Path destination = directory.resolve("from-android.bin");
        CompletableFuture<Path> received = new CompletableFuture<>();
        try (TransferNode node = new TransferNode(50071, new TransferNode.Listener() {
            @Override public CompletableFuture<Path> accept(TransferNode.Offer offer) {
                return CompletableFuture.completedFuture(destination);
            }
            @Override public void received(Path file) { received.complete(file); }
            @Override public void failed(String error) { received.completeExceptionally(new AssertionError(error)); }
        })) {
            System.out.println("READY: desktop port " + node.port());
            received.get(90, TimeUnit.SECONDS);
            byte[] expected = new byte[1048577];
            new Random(42).nextBytes(expected);
            if (!Arrays.equals(expected, Files.readAllBytes(destination))) throw new AssertionError("Received bytes differ");
            try (TransferClient client = new TransferClient("127.0.0.1", 50072)) {
                client.send(destination, "desktop-to-android.bin", "Desktop", () -> {}, (a, b) -> {});
            }
            System.out.println("PASS: Android -> desktop -> Android; 1048577 bytes verified in both directions");
        } finally {
            Files.deleteIfExists(destination);
            Files.deleteIfExists(directory);
        }
    }
}
