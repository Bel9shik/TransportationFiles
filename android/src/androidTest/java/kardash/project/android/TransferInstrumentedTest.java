package kardash.project.android;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import kardash.project.transfer.TransferClient;
import kardash.project.transfer.TransferNode;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.file.*;
import java.util.Random;
import java.util.concurrent.*;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class TransferInstrumentedTest {
    @Test public void exchangesWithDesktopWhenRequested() throws Exception {
        org.junit.Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("desktopInterop")));
        Path directory = Files.createTempDirectory(InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getCacheDir().toPath(), "interop-");
        byte[] data = new byte[1048577];
        new Random(42).nextBytes(data);
        Path source = Files.write(directory.resolve("source"), data);
        Path destination = directory.resolve("from-desktop.bin");
        CompletableFuture<Path> received = new CompletableFuture<>();
        try (TransferNode node = new TransferNode(50072, new TransferNode.Listener() {
            @Override public CompletableFuture<Path> accept(TransferNode.Offer offer) {
                return CompletableFuture.completedFuture(destination);
            }
            @Override public void received(Path file) { received.complete(file); }
            @Override public void failed(String error) { received.completeExceptionally(new AssertionError(error)); }
        }); TransferClient client = new TransferClient("10.0.2.2", 50071)) {
            client.send(source, "android-to-desktop.bin", "Android", () -> {}, (a, b) -> {});
            assertEquals(destination, received.get(30, TimeUnit.SECONDS));
            assertArrayEquals(data, Files.readAllBytes(destination));
        } finally {
            Files.deleteIfExists(source);
            Files.deleteIfExists(destination);
            Files.deleteIfExists(directory);
        }
    }

    @Test public void sendsAndReceivesOnAndroidRuntime() throws Exception {
        Path directory = Files.createTempDirectory(InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getCacheDir().toPath(), "transport-test-");
        try {
            for (int size : new int[]{0, 1048577}) {
                byte[] data = new byte[size];
                new Random(42).nextBytes(data);
                Path source = Files.write(directory.resolve("source"), data);
                Path destination = directory.resolve("получено-" + size + ".bin");
                CompletableFuture<Path> received = new CompletableFuture<>();
                try (TransferNode node = new TransferNode(0, new TransferNode.Listener() {
                    @Override public CompletableFuture<Path> accept(TransferNode.Offer offer) {
                        return CompletableFuture.completedFuture(destination);
                    }
                    @Override public void received(Path file) { received.complete(file); }
                    @Override public void failed(String error) { received.completeExceptionally(new AssertionError(error)); }
                }); TransferClient client = new TransferClient("127.0.0.1", node.port())) {
                    client.send(source, destination.getFileName().toString(), "Android", () -> {}, (a, b) -> {});
                    assertEquals(destination, received.get(10, TimeUnit.SECONDS));
                    assertArrayEquals(data, Files.readAllBytes(destination));
                }
            }
        } finally {
            try (var files = Files.list(directory)) {
                for (Path file : (Iterable<Path>) files::iterator) Files.deleteIfExists(file);
            }
            Files.deleteIfExists(directory);
        }
    }
}
