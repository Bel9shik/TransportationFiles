package kardash.project.transfer;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.okhttp.OkHttpChannelBuilder;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import kardash.project.proto.transport.FileTransferServiceGrpc;
import kardash.project.proto.transport.PairingServiceGrpc;
import kardash.project.proto.transport.TransportFiles;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

public final class TransferClient implements AutoCloseable {
    public record Source(Path path, String name) {}

    public static List<Source> sources(List<Source> files) {
        if (files.isEmpty() || files.size() > 200) throw new IllegalArgumentException("Выберите от 1 до 200 файлов");
        Set<String> names = new HashSet<>();
        List<Source> result = new ArrayList<>();
        for (Source file : files) {
            String name = TransferNode.validateFilename(file.name());
            int dot = name.lastIndexOf('.');
            String stem = dot > 0 ? name.substring(0, dot) : name;
            String extension = dot > 0 ? name.substring(dot) : "";
            int suffix = 1;
            while (!names.add(java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFC).toLowerCase(Locale.ROOT))) name = stem + " (" + suffix++ + ")" + extension;
            result.add(new Source(file.path(), TransferNode.validateFilename(name)));
        }
        return Collections.unmodifiableList(result);
    }

    private final ManagedChannel channel;
    private final String endpoint;

    public TransferClient(String host, int port) {
        if (host.trim().isEmpty() || port < 1 || port > 65535) throw new IllegalArgumentException("Укажите адрес и порт 1–65535");
        endpoint = (host.contains(":") ? "[" + host.trim() + "]" : host.trim()) + ":" + port;
        channel = OkHttpChannelBuilder.forAddress(host.trim(), port).usePlaintext().build();
    }

    public void send(Path file, String name, String sender, Runnable accepted, BiConsumer<Long, Long> progress)
            throws IOException, InterruptedException {
        send(Collections.singletonList(new Source(file, name)), sender, accepted, progress);
    }

    public void send(List<Source> selection, String sender, Runnable accepted, BiConsumer<Long, Long> progress)
            throws IOException, InterruptedException {
        List<Source> files = sources(selection);
        List<Long> sizes = new ArrayList<>();
        long total = 0;
        for (Source file : files) {
            if (!Files.isRegularFile(file.path())) throw new IOException("Файл недоступен: " + file.name());
            long size = Files.size(file.path());
            sizes.add(size);
            total = Math.addExact(total, size);
        }
        var request = TransportFiles.PairingRequest.newBuilder().setPCName(sender)
                .setFileName(files.size() == 1 ? files.get(0).name() : "Файлы-" + System.currentTimeMillis())
                .setFileSize(Long.toString(total)).setSizeBytes(total).setProtocolVersion(files.size() == 1 ? 1 : 2);
        if (files.size() > 1) for (int i = 0; i < files.size(); i++) {
            request.addFiles(TransportFiles.FileInfo.newBuilder().setName(files.get(i).name()).setSize(sizes.get(i)));
        }
        try {
            var approval = PairingServiceGrpc.newBlockingStub(channel).withDeadlineAfter(65, TimeUnit.SECONDS)
                    .pairingRequest(request.build());
            if (!approval.getResponse()) throw new IOException("Получатель отклонил запрос");
            if (approval.getTransferToken().isEmpty()) throw new IOException("Получатель использует старую версию приложения");
            accepted.run();
            stream(files, sizes, total, approval.getTransferToken(), progress);
        } catch (io.grpc.StatusRuntimeException e) {
            if (e.getStatus().getCode() == io.grpc.Status.Code.UNAVAILABLE) {
                throw new IOException("Не удалось связаться с получателем " + endpoint
                        + ". Проверьте, что приложение открыто и входящие соединения разрешены в firewall и VPN."
                        + " Причина: " + e.getStatus(), e);
            }
            throw new IOException(e.getStatus().getDescription() == null ? e.toString() : e.getStatus().getDescription(), e);
        }
    }

    private void stream(List<Source> files, List<Long> sizes, long total, String token, BiConsumer<Long, Long> progress)
            throws IOException, InterruptedException {
        CompletableFuture<TransportFiles.TransferStatus> result = new CompletableFuture<>();
        Object ready = new Object();
        class Response implements ClientResponseObserver<TransportFiles.FileChunk, TransportFiles.TransferStatus> {
            ClientCallStreamObserver<TransportFiles.FileChunk> request;
            TransportFiles.TransferStatus status;
            @Override public void beforeStart(ClientCallStreamObserver<TransportFiles.FileChunk> request) {
                this.request = request;
                request.setOnReadyHandler(() -> { synchronized (ready) { ready.notifyAll(); } });
            }
            @Override public void onNext(TransportFiles.TransferStatus value) { status = value; }
            @Override public void onError(Throwable error) {
                result.completeExceptionally(error);
                synchronized (ready) { ready.notifyAll(); }
            }
            @Override public void onCompleted() {
                if (status == null) result.completeExceptionally(new IOException("Получатель не подтвердил сохранение"));
                else result.complete(status);
            }
        }
        Response response = new Response();
        FileTransferServiceGrpc.newStub(channel).withDeadlineAfter(10, TimeUnit.MINUTES).sendFile(response);
        long transferred = 0;
        try {
            for (int i = 0; i < files.size(); i++) {
                Source file = files.get(i);
                long size = sizes.get(i), count = 0;
                var chunk = TransportFiles.FileChunk.newBuilder().setFilename(file.name()).setTransferToken(token);
                MessageDigest hash = TransferNode.digest();
                try (InputStream input = Files.newInputStream(file.path())) {
                    byte[] buffer = new byte[64 * 1024];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        synchronized (ready) {
                            while (!response.request.isReady() && !result.isDone()) ready.wait(250);
                        }
                        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                        if (result.isDone()) result.get();
                        if (read > size - count) throw new IOException("Размер исходного файла изменился: " + file.name());
                        hash.update(buffer, 0, read);
                        response.request.onNext(chunk.setContent(ByteString.copyFrom(buffer, 0, read)).build());
                        count += read;
                        progress.accept(transferred + count, total);
                    }
                }
                if (count != size) throw new IOException("Размер исходного файла изменился: " + file.name());
                response.request.onNext(chunk.clearContent().setSha256(ByteString.copyFrom(hash.digest())).build());
                transferred += count;
            }
            response.request.onCompleted();
            var status = result.get(10, TimeUnit.MINUTES);
            if (!status.getSuccess()) throw new IOException(status.getMessage());
            progress.accept(total, total);
        } catch (ExecutionException | TimeoutException e) {
            response.request.cancel("Transfer failed", e);
            throw new IOException("Передача не завершена: " + e.getMessage(), e);
        } catch (IOException | InterruptedException | RuntimeException e) {
            response.request.cancel("Transfer cancelled", e);
            throw e;
        }
    }

    @Override public void close() { channel.shutdownNow(); }
}
