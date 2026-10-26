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
import java.util.*;
import java.nio.file.LinkOption;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** One explicitly approved incoming transfer at a time, independent of the UI toolkit. */
public final class TransferNode implements AutoCloseable {
    public record FileInfo(String name, long size) {}
    public record Offer(String sender, String filename, long size, List<FileInfo> files) {
        public Offer(String sender, String filename, long size) { this(sender, filename, size, Collections.singletonList(new FileInfo(filename, size))); }
        public Offer { files = Collections.unmodifiableList(new ArrayList<>(files)); }
        public boolean multiple() { return files.size() > 1; }
        public String description() { return String.join("\n", files.stream().map(FileInfo::name).collect(java.util.stream.Collectors.toList())); }
    }

    static Offer offer(TransportFiles.PairingRequest request) {
        int version = request.getProtocolVersion();
        if (version != 1 && version != 2) throw new IllegalArgumentException("Обновите приложение на обоих устройствах");
        validateFilename(request.getFileName());
        if (request.getSizeBytes() < 0) throw new IllegalArgumentException("Некорректный размер");
        if (version == 1) {
            if (request.getFilesCount() != 0) throw new IllegalArgumentException("Некорректный список файлов");
            return new Offer(request.getPCName(), request.getFileName(), request.getSizeBytes());
        }
        if (request.getFilesCount() < 2 || request.getFilesCount() > 200) throw new IllegalArgumentException("Допустимо от 2 до 200 файлов");
        List<FileInfo> files = new ArrayList<>();
        Set<String> names = new HashSet<>();
        long total = 0;
        for (var file : request.getFilesList()) {
            String name = validateFilename(file.getName());
            if (!names.add(java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFC).toLowerCase(Locale.ROOT)) || file.getSize() < 0 || file.getSize() > Long.MAX_VALUE - total) {
                throw new IllegalArgumentException("Некорректные имена или размеры файлов");
            }
            total += file.getSize();
            files.add(new FileInfo(name, file.getSize()));
        }
        if (total != request.getSizeBytes()) throw new IllegalArgumentException("Не совпадает общий размер файлов");
        return new Offer(request.getPCName(), request.getFileName(), total, files);
    }

    public static List<Path> savedFiles(Path target) throws IOException {
        if (!Files.isDirectory(target)) return Collections.singletonList(target);
        try (var files = Files.list(target)) { return files.filter(Files::isRegularFile).sorted().collect(java.util.stream.Collectors.toList()); }
    }

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
            Offer offer;
            try { offer = offer(request); }
            catch (IllegalArgumentException e) {
                response.onError(Status.FAILED_PRECONDITION.withDescription(e.getMessage()).asRuntimeException());
                return;
            }
            Session session = new Session(offer);
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
                        if (Files.exists(session.target, LinkOption.NOFOLLOW_LINKS) || !Files.isDirectory(session.target.getParent())) {
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
        private long completedBytes;
        private int fileIndex;
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
                            || !candidate.offer.files().get(0).name().equals(chunk.getFilename())
                            || !candidate.started.compareAndSet(false, true)) {
                        throw new IOException("Нет разрешения на эту передачу");
                    }
                    session = candidate;
                    session.cancel = () -> fail(new IOException("Приём отменён"));
                    temporary = session.offer.multiple()
                            ? Files.createTempDirectory(session.target.getParent(), ".transport-")
                            : Files.createTempFile(session.target.getParent(), ".transport-", ".part");
                }
                if (current.get() != session || !session.token.equals(chunk.getTransferToken())
                        || fileIndex >= session.offer.files().size()
                        || !session.offer.files().get(fileIndex).name().equals(chunk.getFilename())) {
                    throw new IOException("Некорректная последовательность блоков");
                }
                FileInfo file = session.offer.files().get(fileIndex);
                if (output == null) output = Files.newOutputStream(session.offer.multiple() ? temporary.resolve(file.name()) : temporary);
                ByteString content = chunk.getContent();
                if (content.size() > file.size() - count) throw new IOException("Размер файла превышен");
                if (!chunk.getSha256().isEmpty()) {
                    if (!content.isEmpty() || chunk.getSha256().size() != 32 || count != file.size()
                            || !MessageDigest.isEqual(hash.digest(), chunk.getSha256().toByteArray())) {
                        throw new IOException("Файл повреждён или получен не полностью: " + file.name());
                    }
                    output.close();
                    output = null;
                    completedBytes += count;
                    count = 0;
                    fileIndex++;
                } else {
                    content.writeTo(output);
                    hash.update(content.asReadOnlyByteBuffer());
                    count += content.size();
                    listener.progress(completedBytes + count, session.offer.size());
                }
            } catch (Exception e) { fail(e); }
        }

        @Override
        public synchronized void onCompleted() {
            if (done) return;
            try {
                if (session == null || fileIndex != session.offer.files().size() || completedBytes != session.offer.size()) {
                    throw new IOException("Файлы получены не полностью");
                }
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
            try {
                if (temporary != null) {
                    if (Files.isDirectory(temporary)) {
                        try (var files = Files.list(temporary)) {
                            for (Path file : files.collect(java.util.stream.Collectors.toList())) Files.deleteIfExists(file);
                        }
                    }
                    Files.deleteIfExists(temporary);
                }
            }
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
