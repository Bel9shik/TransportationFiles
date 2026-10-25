package kardash.project.transfer;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.okhttp.OkHttpChannelBuilder;
import io.grpc.stub.StreamObserver;
import kardash.project.proto.transport.FileTransferServiceGrpc;
import kardash.project.proto.transport.PairingServiceGrpc;
import kardash.project.proto.transport.TransportFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.*;
import java.util.Random;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class TransferTest {
    @TempDir Path directory;

    private static class Listener implements TransferNode.Listener {
        final CompletableFuture<Path> answer;
        final CompletableFuture<Path> received = new CompletableFuture<>();
        final CompletableFuture<String> failed = new CompletableFuture<>();
        final CountDownLatch offered = new CountDownLatch(1);
        Listener(Path target) { answer = CompletableFuture.completedFuture(target); }
        Listener() { answer = new CompletableFuture<>(); }
        @Override public CompletableFuture<Path> accept(TransferNode.Offer offer) { offered.countDown(); return answer; }
        @Override public void received(Path file) { received.complete(file); }
        @Override public void failed(String message) { failed.complete(message); }
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2097153})
    void transfersBytesIncludingEmptyAndMultipleChunks(int size) throws Exception {
        byte[] bytes = new byte[size];
        new Random(42).nextBytes(bytes);
        Path source = directory.resolve("source");
        Files.write(source, bytes);
        Path target = directory.resolve("файл с пробелами.bin");
        Listener listener = new Listener(target);
        try (TransferNode node = new TransferNode(0, listener);
             TransferClient client = new TransferClient("127.0.0.1", node.port())) {
            client.send(source, target.getFileName().toString(), "Test", () -> {}, (a, b) -> {});
            assertEquals(target, listener.received.get(5, TimeUnit.SECONDS));
            assertArrayEquals(bytes, Files.readAllBytes(target));
            assertNoPartialFiles();
        }
    }

    @Test void refusalDoesNotCreateFile() throws Exception {
        Path source = Files.write(directory.resolve("source"), new byte[]{1});
        try (TransferNode node = new TransferNode(0, new Listener(null));
             TransferClient client = new TransferClient("127.0.0.1", node.port())) {
            assertThrows(IOException.class, () -> client.send(source, "file", "Test", () -> {}, (a, b) -> {}));
            assertNoPartialFiles();
        }
    }

    @Test void doesNotOverwriteExistingFile() throws Exception {
        Path source = Files.write(directory.resolve("source"), new byte[]{1});
        Path target = Files.write(directory.resolve("target"), new byte[]{7});
        try (TransferNode node = new TransferNode(0, new Listener(target));
             TransferClient client = new TransferClient("127.0.0.1", node.port())) {
            assertThrows(IOException.class, () -> client.send(source, "file", "Test", () -> {}, (a, b) -> {}));
            assertArrayEquals(new byte[]{7}, Files.readAllBytes(target));
        }
    }

    @Test void cancellationDismissesPendingApprovalPrompt() throws Exception {
        Listener listener = new Listener();
        try (TransferNode node = new TransferNode(0, listener)) {
            ManagedChannel channel = channel(node);
            try {
                CompletableFuture<TransportFiles.PairingResponse> response = new CompletableFuture<>();
                PairingServiceGrpc.newStub(channel).withDeadlineAfter(5, TimeUnit.SECONDS)
                        .pairingRequest(request("file", 1), observer(response));
                assertTrue(listener.offered.await(3, TimeUnit.SECONDS));
                channel.shutdownNow();
                assertNull(listener.answer.get(3, TimeUnit.SECONDS));
            } finally { channel.shutdownNow(); }
        }
    }

    @ParameterizedTest @ValueSource(strings = {"wrong-token", "bad-hash", "truncated", "oversized", "changed-name"})
    void rejectsInvalidStreamsAndRemovesPartialFile(String mode) throws Exception {
        Path target = directory.resolve("target");
        Listener listener = new Listener(target);
        try (TransferNode node = new TransferNode(0, listener)) {
            ManagedChannel channel = channel(node);
            try {
                String token = PairingServiceGrpc.newBlockingStub(channel).withDeadlineAfter(5, TimeUnit.SECONDS)
                        .pairingRequest(request("file", 1)).getTransferToken();
                CompletableFuture<TransportFiles.TransferStatus> result = new CompletableFuture<>();
                StreamObserver<TransportFiles.FileChunk> upload = FileTransferServiceGrpc.newStub(channel)
                        .withDeadlineAfter(5, TimeUnit.SECONDS).sendFile(observer(result));
                var chunk = TransportFiles.FileChunk.newBuilder().setFilename("file")
                        .setTransferToken(mode.equals("wrong-token") ? "wrong" : token);
                if (!mode.equals("truncated")) {
                    upload.onNext(chunk.setContent(ByteString.copyFrom(new byte[mode.equals("oversized") ? 2 : 1])).build());
                }
                if (mode.equals("changed-name")) chunk.setFilename("another");
                upload.onNext(chunk.clearContent().setSha256(ByteString.copyFrom(new byte[32])).build());
                upload.onCompleted();
                assertThrows(ExecutionException.class, () -> result.get(6, TimeUnit.SECONDS));
                assertFalse(Files.exists(target));
                assertNoPartialFiles();
            } finally { channel.shutdownNow(); }
        }
    }

    @Test void targetCreatedAfterApprovalIsNotReplaced() throws Exception {
        Path target = directory.resolve("target");
        Listener listener = new Listener(target);
        try (TransferNode node = new TransferNode(0, listener)) {
            ManagedChannel channel = channel(node);
            try {
                String token = PairingServiceGrpc.newBlockingStub(channel).withDeadlineAfter(5, TimeUnit.SECONDS)
                        .pairingRequest(request("file", 0)).getTransferToken();
                Files.write(target, new byte[]{7});
                CompletableFuture<TransportFiles.TransferStatus> result = new CompletableFuture<>();
                var stream = FileTransferServiceGrpc.newStub(channel).withDeadlineAfter(5, TimeUnit.SECONDS).sendFile(observer(result));
                stream.onNext(TransportFiles.FileChunk.newBuilder().setFilename("file").setTransferToken(token)
                        .setSha256(ByteString.copyFrom(TransferNode.digest().digest())).build());
                stream.onCompleted();
                assertThrows(ExecutionException.class, () -> result.get(6, TimeUnit.SECONDS));
                assertArrayEquals(new byte[]{7}, Files.readAllBytes(target));
                assertNoPartialFiles();
            } finally { channel.shutdownNow(); }
        }
    }

    @Test void receiverCancellationRemovesPartialDataAndAllowsNextTransfer() throws Exception {
        Path target = directory.resolve("target");
        Listener listener = new Listener(target);
        try (TransferNode node = new TransferNode(0, listener)) {
            ManagedChannel channel = channel(node);
            try {
                String token = PairingServiceGrpc.newBlockingStub(channel).withDeadlineAfter(5, TimeUnit.SECONDS)
                        .pairingRequest(request("file", 2)).getTransferToken();
                CompletableFuture<TransportFiles.TransferStatus> result = new CompletableFuture<>();
                var stream = FileTransferServiceGrpc.newStub(channel).withDeadlineAfter(5, TimeUnit.SECONDS).sendFile(observer(result));
                stream.onNext(TransportFiles.FileChunk.newBuilder().setFilename("file").setTransferToken(token)
                        .setContent(ByteString.copyFrom(new byte[]{1})).build());
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
                boolean partialExists = false;
                while (!partialExists && System.nanoTime() < deadline) {
                    try (var files = Files.list(directory)) { partialExists = files.anyMatch(p -> p.toString().endsWith(".part")); }
                    if (!partialExists) Thread.sleep(10);
                }
                assertTrue(partialExists);
                node.cancelIncoming();
                assertThrows(ExecutionException.class, () -> result.get(5, TimeUnit.SECONDS));
                assertNoPartialFiles();
                assertFalse(Files.exists(target));
                Path source = Files.write(directory.resolve("source"), new byte[]{9});
                try (TransferClient client = new TransferClient("127.0.0.1", node.port())) {
                    client.send(source, "file", "Test", () -> {}, (a, b) -> {});
                }
                assertArrayEquals(new byte[]{9}, Files.readAllBytes(target));
            } finally { channel.shutdownNow(); }
        }
    }

    @Test void rejectsUtf8NamesTooLongForPortableFilesystem() {
        assertThrows(IllegalArgumentException.class, () -> TransferNode.validateFilename("я".repeat(121)));
    }

    @Test void unapprovedUploadIsRejected() throws Exception {
        try (TransferNode node = new TransferNode(0, new Listener(directory.resolve("target")))) {
            ManagedChannel channel = channel(node);
            try {
                CompletableFuture<TransportFiles.TransferStatus> result = new CompletableFuture<>();
                var stream = FileTransferServiceGrpc.newStub(channel).withDeadlineAfter(5, TimeUnit.SECONDS).sendFile(observer(result));
                stream.onNext(TransportFiles.FileChunk.newBuilder().setFilename("file").build());
                stream.onCompleted();
                assertThrows(ExecutionException.class, () -> result.get(6, TimeUnit.SECONDS));
                assertNoPartialFiles();
            } finally { channel.shutdownNow(); }
        }
    }

    @ParameterizedTest @ValueSource(strings = {"", "..", "../escape", "a\\b", "/tmp/escape", "C:escape", "NUL", "con.txt", "trailing.", "trailing ", "bad\nname", "bad\nname\nagain"})
    void rejectsUnsafeNamesOnEveryPlatform(String name) {
        assertThrows(IllegalArgumentException.class, () -> TransferNode.validateFilename(name));
    }

    @Test void discoveryUsesPacketSourceAndIgnoresMalformedPackets() {
        assertEquals(new Discovery.Peer("192.0.2.3", 1234, "Телефон"),
                Discovery.decode("TF1\nother\n1234\nТелефон", "192.0.2.3", "me"));
        for (String data : new String[]{"", "TF1\nme\n1234\nSelf", "TF1\nx\nno-port\nPeer", "TF1\nx\n65536\nPeer"}) {
            assertNull(Discovery.decode(data, "192.0.2.3", "me"));
        }
        assertEquals("::1", Discovery.endpoint("[::1]:8080").host());
        assertThrows(IllegalArgumentException.class, () -> Discovery.endpoint("localhost:0"));
    }

    private void assertNoPartialFiles() throws IOException {
        try (var files = Files.list(directory)) {
            assertTrue(files.noneMatch(path -> path.toString().endsWith(".part")));
        }
    }
    private static ManagedChannel channel(TransferNode node) {
        return OkHttpChannelBuilder.forAddress("127.0.0.1", node.port()).usePlaintext().build();
    }
    private static TransportFiles.PairingRequest request(String name, long size) {
        return TransportFiles.PairingRequest.newBuilder().setFileName(name).setSizeBytes(size).setProtocolVersion(1).build();
    }
    private static <T> StreamObserver<T> observer(CompletableFuture<T> future) {
        return new StreamObserver<>() {
            T value;
            @Override public void onNext(T next) { value = next; }
            @Override public void onError(Throwable error) { future.completeExceptionally(error); }
            @Override public void onCompleted() { future.complete(value); }
        };
    }
}
