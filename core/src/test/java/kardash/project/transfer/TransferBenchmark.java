package kardash.project.transfer;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Real transfer core; the Python driver checks every saved file outside the timer. */
public final class TransferBenchmark {
    public static void main(String[] args) throws Exception {
        if (args[0].equals("serve")) {
            Path root = Path.of(args[1]);
            try (TransferNode node = new TransferNode(0, new TransferNode.Listener() {
                public CompletableFuture<Path> accept(TransferNode.Offer offer) {
                    return CompletableFuture.completedFuture(root.resolve(UUID.randomUUID().toString()));
                }
                public void received(Path file) {}
                public void failed(String error) { System.err.println(error); }
            })) {
                Files.writeString(root.resolve("port"), Integer.toString(node.port()));
                System.in.read();
            }
        } else if (args[0].equals("send")) {
            int port = Integer.parseInt(args[1]);
            int runs = Integer.parseInt(args[2]);
            List<TransferClient.Source> files = new ArrayList<>();
            for (int i = 3; i < args.length; i++) {
                Path path = Path.of(args[i]);
                files.add(new TransferClient.Source(path, path.getFileName().toString()));
            }
            // Reuse the connection, as the app does; warmup samples remain in the output.
            try (TransferClient sender = new TransferClient("127.0.0.1", port)) {
                for (int i = 0; i < runs; i++) {
                    long started = System.nanoTime();
                    sender.send(files, "Benchmark", () -> {}, (a, b) -> {});
                    System.out.println((System.nanoTime() - started) / 1e9);
                }
            }
        } else {
            throw new IllegalArgumentException("Expected serve or send");
        }
    }
}
