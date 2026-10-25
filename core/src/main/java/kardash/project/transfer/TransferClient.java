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
import java.util.concurrent.*;
import java.util.function.BiConsumer;

public final class TransferClient implements AutoCloseable {
    private final ManagedChannel channel;

    public TransferClient(String host, int port) {
        if (host.trim().isEmpty() || port < 1 || port > 65535) throw new IllegalArgumentException("Укажите адрес и порт 1–65535");
        channel = OkHttpChannelBuilder.forAddress(host.trim(), port).usePlaintext().build();
    }

    public void send(Path file, String name, String sender, Runnable accepted, BiConsumer<Long, Long> progress)
            throws IOException, InterruptedException {
        TransferNode.validateFilename(name);
        long size = Files.size(file);
        try {
            var approval = PairingServiceGrpc.newBlockingStub(channel).withDeadlineAfter(65, TimeUnit.SECONDS)
                    .pairingRequest(TransportFiles.PairingRequest.newBuilder()
                            .setPCName(sender).setFileName(name).setFileSize(Long.toString(size))
                            .setSizeBytes(size).setProtocolVersion(1).build());
            if (!approval.getResponse()) throw new IOException("Получатель отклонил запрос");
            if (approval.getTransferToken().isEmpty()) throw new IOException("Получатель использует старую версию приложения");
            accepted.run();
            stream(file, name, size, approval.getTransferToken(), progress);
        } catch (io.grpc.StatusRuntimeException e) {
            throw new IOException(e.getStatus().getDescription() == null ? e.toString() : e.getStatus().getDescription(), e);
        }
    }

    private void stream(Path file, String name, long size, String token, BiConsumer<Long, Long> progress)
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
        var chunk = TransportFiles.FileChunk.newBuilder().setFilename(name).setTransferToken(token);
        MessageDigest hash = TransferNode.digest();
        long count = 0;
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                synchronized (ready) {
                    while (!response.request.isReady() && !result.isDone()) ready.wait(250);
                }
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                if (result.isDone()) result.get();
                hash.update(buffer, 0, read);
                response.request.onNext(chunk.setContent(ByteString.copyFrom(buffer, 0, read)).build());
                count += read;
                progress.accept(count, size);
            }
            if (count != size) throw new IOException("Размер исходного файла изменился во время отправки");
            response.request.onNext(chunk.clearContent().setSha256(ByteString.copyFrom(hash.digest())).build());
            response.request.onCompleted();
            var status = result.get(10, TimeUnit.MINUTES);
            if (!status.getSuccess()) throw new IOException(status.getMessage());
            progress.accept(size, size);
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
