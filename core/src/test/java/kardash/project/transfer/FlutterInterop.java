package kardash.project.transfer;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Bidirectional wire compatibility between the released Java core and Dart. */
public final class FlutterInterop {
    public static void main(String[] args) throws Exception {
        Path exchange = Path.of(args[0]);
        Path work = Files.createTempDirectory("java-flutter-interop-");
        BlockingQueue<Path> received = new LinkedBlockingQueue<>();
        List<String> failures = new CopyOnWriteArrayList<>();
        try (TransferNode node = new TransferNode(0, new TransferNode.Listener() {
            public CompletableFuture<Path> accept(TransferNode.Offer offer) {
                return CompletableFuture.completedFuture(work.resolve("incoming-" + UUID.randomUUID()));
            }
            public void received(Path file) { received.add(file); }
            public void failed(String error) { failures.add(error); }
        })) {
            Files.writeString(exchange.resolve("java-port"), Integer.toString(node.port()));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
            while (!Files.exists(exchange.resolve("dart-port"))) {
                if (System.nanoTime() > deadline) throw new AssertionError("Dart did not start");
                Thread.sleep(50);
            }
            int port = Integer.parseInt(Files.readString(exchange.resolve("dart-port")));
            byte[] bytes = new byte[1048577];
            for (int i = 0; i < bytes.length; i++) bytes[i] = (byte)(i % 251);
            Path data = Files.write(work.resolve("data"), bytes);
            Path empty = Files.write(work.resolve("empty"), new byte[0]);
            try (TransferClient sender = new TransferClient("127.0.0.1", port)) {
                sender.send(data, "Фото 🌆.bin", "Java", () -> {}, (a,b) -> {});
                sender.send(List.of(new TransferClient.Source(data, "Фото.bin"), new TransferClient.Source(empty, "Фото.bin")), "Java", () -> {}, (a,b) -> {});
            }
            Path single = received.poll(90, TimeUnit.SECONDS);
            Path batch = received.poll(90, TimeUnit.SECONDS);
            if (single == null || batch == null || !failures.isEmpty()) throw new AssertionError("Receive failed: " + failures);
            if (!Arrays.equals(bytes, Files.readAllBytes(single))) throw new AssertionError("Single differs");
            if (!Arrays.equals(bytes, Files.readAllBytes(batch.resolve("Фото.bin"))) || Files.size(batch.resolve("Фото (1).bin")) != 0) throw new AssertionError("Batch differs");
            System.out.println("PASS Java <-> Dart: single + batch + duplicate names + empty file + Unicode + SHA256");
        } finally {
            try (var files = Files.walk(work)) { for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file); }
        }
    }
}
