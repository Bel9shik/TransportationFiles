package kardash.project.transfer;

import com.google.protobuf.ByteString;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.okhttp.OkHttpServerBuilder;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import kardash.project.proto.transport.FileTransferServiceGrpc;
import kardash.project.proto.transport.PairingServiceGrpc;
import kardash.project.proto.transport.TransportFiles;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** One explicitly approved incoming transfer at a time, independent of the UI toolkit. */
public final class TransferNode implements AutoCloseable {
    public record Offer(String sender, String filename, long size) {}

    public interface Listener {
        CompletableFuture<Path> accept(Offer offer);
        default void progress(long bytes, long total) {}
        void received(Path file);
        void failed(String message);
    }

    private static final class Session {
        final Offer offer;
        final String token = UUID.randomUUID().toString();
        final AtomicBoolean started = new AtomicBoolean();
        final CompletableFuture<Path> answer = new CompletableFuture<>();
        volatile Path target;
        volatile Runnable cancel;
        Session(Offer offer) { this.offer = offer; }
    }

    private final Listener listener;
    private final AtomicReference<Session> current = new AtomicReference<>();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    private final Server server;

    public TransferNode(int port, Listener listener) throws IOException {
        this.listener = listener;
        try {
            server = OkHttpServerBuilder.forPort(port, InsecureServerCredentials.create())
                    .maxInboundMessageSize(128 * 1024)
                    .addService(new Pairing())
                    .addService(new Receiver())
                    .build().start();
        } catch (IOException | RuntimeException e) {
            timer.shutdownNow();
            throw e;
        }
    }

    public int port() { return server.getPort(); }

    public static String validateFilename(String name) {
        if (name.isEmpty() || name.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 240 || name.equals(".") || name.equals("..")
                || name.endsWith(".") || name.endsWith(" ")
                || name.matches("(?s).*[\\\\/:*?\"<>|\\p{Cntrl}].*")
                || name.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?")) {
            throw new IllegalArgumentException("Недопустимое имя файла: " + name);
        }
        return name;
    }

    static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }

    private final class Pairing extends PairingServiceGrpc.PairingServiceImplBase {
        @Override
        public void pairingRequest(TransportFiles.PairingRequest request,
                                   StreamObserver<TransportFiles.PairingResponse> response) {
            if (request.getProtocolVersion() != 1 || request.getSizeBytes() < 0) {
                response.onError(Status.FAILED_PRECONDITION
                        .withDescription("Обновите приложение на обоих устройствах").asRuntimeException());
                return;
            }
            try { validateFilename(request.getFileName()); }
            catch (IllegalArgumentException e) {
                response.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
                return;
            }
            Session session = new Session(new Offer(request.getPCName(), request.getFileName(), request.getSizeBytes()));
            if (!current.compareAndSet(null, session)) {
                response.onError(Status.RESOURCE_EXHAUSTED.withDescription("Получатель занят").asRuntimeException());
                return;
            }
            var call = (ServerCallStreamObserver<TransportFiles.PairingResponse>) response;
            AtomicBoolean replied = new AtomicBoolean();
            call.setOnCancelHandler(() -> {
                if (!replied.get()) session.answer.complete(null);
            });
            CompletableFuture<Path> uiAnswer;
            try { uiAnswer = listener.accept(session.offer); }
            catch (Exception e) {
                current.compareAndSet(session, null);
                response.onError(Status.INTERNAL.withDescription(e.toString()).asRuntimeException());
                return;
            }
            ScheduledFuture<?> deadline = timer.schedule(() -> session.answer.completeExceptionally(
                    new TimeoutException("Время ожидания подтверждения истекло")), 60, TimeUnit.SECONDS);
            session.answer.whenComplete((target, failure) -> {
                deadline.cancel(false);
                try {
                    if (failure != null) throw new IOException(failure.getMessage(), failure);
                    if (target == null || call.isCancelled() || current.get() != session) {
                        current.compareAndSet(session, null);
                        response.onNext(TransportFiles.PairingResponse.getDefaultInstance());
                    } else {
                        session.target = target.toAbsolutePath();
                        if (Files.exists(session.target) || !Files.isDirectory(session.target.getParent())) {
                            throw new IOException("Выберите новый файл в существующей папке");
                        }
                        timer.schedule(() -> {
                            if (!session.started.get() && current.compareAndSet(session, null)) {
                                listener.failed("Отправитель не начал передачу");
                            }
                        }, 60, TimeUnit.SECONDS);
                        replied.set(true);
                        response.onNext(TransportFiles.PairingResponse.newBuilder().setResponse(true)
                                .setTransferToken(session.token).build());
                    }
                    response.onCompleted();
                } catch (Exception e) {
                    current.compareAndSet(session, null);
                    response.onError(Status.CANCELLED.withDescription(e.getMessage()).asRuntimeException());
                    listener.failed(e.getMessage());
                } finally {
                    uiAnswer.complete(null);
                }
            });
            uiAnswer.whenComplete((path, failure) -> {
                if (failure != null) session.answer.completeExceptionally(failure);
                else session.answer.complete(path);
            });
        }
    }

    private final class Receiver extends FileTransferServiceGrpc.FileTransferServiceImplBase {
        @Override
        public StreamObserver<TransportFiles.FileChunk> sendFile(StreamObserver<TransportFiles.TransferStatus> response) {
            var call = (ServerCallStreamObserver<TransportFiles.TransferStatus>) response;
            Incoming incoming = new Incoming(response);
            call.setOnCancelHandler(() -> incoming.fail(new IOException("Передача отменена")));
            incoming.timeout = timer.schedule(() -> incoming.fail(new IOException("Время передачи истекло")),
                    10, TimeUnit.MINUTES);
            return incoming;
        }
    }

    private final class Incoming implements StreamObserver<TransportFiles.FileChunk> {
        private final StreamObserver<TransportFiles.TransferStatus> response;
        private final MessageDigest hash = digest();
        private Session session;
        private Path temporary;
        private OutputStream output;
        private long count;
        private byte[] expectedHash;
        private boolean done;
        private ScheduledFuture<?> timeout;

        Incoming(StreamObserver<TransportFiles.TransferStatus> response) { this.response = response; }

        @Override
        public synchronized void onNext(TransportFiles.FileChunk chunk) {
            if (done) return;
            try {
                if (session == null) {
                    Session candidate = current.get();
                    if (candidate == null || candidate.target == null || chunk.getTransferToken().isEmpty()
                            || !candidate.token.equals(chunk.getTransferToken())
                            || !candidate.offer.filename().equals(chunk.getFilename())
                            || !candidate.started.compareAndSet(false, true)) {
                        throw new IOException("Нет разрешения на эту передачу");
                    }
                    session = candidate;
                    session.cancel = () -> fail(new IOException("Приём отменён"));
                    temporary = Files.createTempFile(session.target.getParent(), ".transport-", ".part");
                    output = Files.newOutputStream(temporary);
                }
                if (current.get() != session || !session.token.equals(chunk.getTransferToken())
                        || !session.offer.filename().equals(chunk.getFilename()) || expectedHash != null) {
                    throw new IOException("Некорректная последовательность блоков");
                }
                ByteString content = chunk.getContent();
                if (content.size() > session.offer.size() - count) throw new IOException("Размер файла превышен");
                if (!chunk.getSha256().isEmpty()) {
                    if (!content.isEmpty() || chunk.getSha256().size() != 32) throw new IOException("Некорректная контрольная сумма");
                    expectedHash = chunk.getSha256().toByteArray();
                } else {
                    content.writeTo(output);
                    hash.update(content.asReadOnlyByteBuffer());
                    count += content.size();
                    listener.progress(count, session.offer.size());
                }
            } catch (Exception e) { fail(e); }
        }

        @Override
        public synchronized void onCompleted() {
            if (done) return;
            try {
                if (session == null || count != session.offer.size() || expectedHash == null
                        || !MessageDigest.isEqual(hash.digest(), expectedHash)) {
                    throw new IOException("Файл повреждён или получен не полностью");
                }
                output.close();
                output = null;
                // No REPLACE_EXISTING: a user file created during transfer must survive.
                Files.move(temporary, session.target);
                temporary = null;
                done = true;
                timeout.cancel(false);
                current.compareAndSet(session, null);
                response.onNext(TransportFiles.TransferStatus.newBuilder().setSuccess(true)
                        .setMessage(session.target.getFileName().toString()).build());
                response.onCompleted();
                listener.received(session.target);
            } catch (Exception e) { fail(e); }
        }

        @Override
        public void onError(Throwable error) { fail(error); }

        synchronized void fail(Throwable error) {
            if (done) return;
            done = true;
            if (timeout != null) timeout.cancel(false);
            try { if (output != null) output.close(); }
            catch (IOException e) { error.addSuppressed(e); }
            try { if (temporary != null) Files.deleteIfExists(temporary); }
            catch (IOException e) { error.addSuppressed(e); }
            if (session != null) {
                current.compareAndSet(session, null);
                listener.failed(error.getMessage());
            }
            response.onError(Status.DATA_LOSS.withDescription(error.getMessage()).asRuntimeException());
        }
    }

    public void cancelIncoming() {
        Session session = current.get();
        if (session == null) return;
        session.answer.complete(null);
        Runnable cancel = session.cancel;
        if (cancel != null) cancel.run();
        else current.compareAndSet(session, null);
    }

    @Override
    public void close() {
        Session session = current.getAndSet(null);
        if (session != null) session.answer.complete(null);
        server.shutdownNow();
        timer.shutdownNow();
    }
}
