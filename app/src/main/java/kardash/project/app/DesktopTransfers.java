package kardash.project.app;

import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import kardash.project.app.core.cotrollers.view.ViewController;
import kardash.project.app.models.IncomingRequestContext;
import kardash.project.app.models.TransferContext;
import kardash.project.transfer.Discovery;
import kardash.project.transfer.TransferClient;
import kardash.project.transfer.TransferNode;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public final class DesktopTransfers {
    public static final DoubleProperty progress = new SimpleDoubleProperty(-1);
    public static final StringProperty status = new SimpleStringProperty("");
    public static boolean waiting;
    private static TransferNode node;
    private static TransferClient client;
    private static Future<?> sending;
    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static final AtomicLong lastProgress = new AtomicLong();

    public static void start() throws IOException {
        node = new TransferNode(0, new TransferNode.Listener() {
            @Override public CompletableFuture<Path> accept(TransferNode.Offer offer) {
                CompletableFuture<Path> answer = new CompletableFuture<>();
                Platform.runLater(() -> {
                    if (!waiting || answer.isDone()) { answer.complete(null); return; }
                    waiting = false;
                    IncomingRequestContext context = new IncomingRequestContext(offer, answer);
                    TransferContext.setIncomingRequestContext(context);
                    reset("Приём: " + offer.filename());
                    show("incoming_request.fxml");
                });
                return answer;
            }
            @Override public void progress(long bytes, long total) { updateProgress(bytes, total); }
            @Override public void received(Path file) {
                Platform.runLater(() -> {
                    progress.set(1);
                    status.set("Сохранено: " + file);
                });
            }
            @Override public void failed(String message) {
                Platform.runLater(() -> { progress.set(0); status.set("Ошибка: " + message); });
            }
        });
    }

    public static int port() { return node.port(); }
    public static String name() { return System.getProperty("user.name", "Desktop"); }

    public static void reset(String message) {
        lastProgress.set(0);
        progress.set(-1);
        status.set(message);
    }

    public static void send() {
        var user = TransferContext.getAnotherUser();
        var file = TransferContext.getFile().file().toPath();
        TransferClient transfer = new TransferClient(user.ip(), user.port());
        client = transfer;
        reset("Ожидание подтверждения: " + file.getFileName());
        sending = worker.submit(() -> {
            try (transfer) {
                transfer.send(file, file.getFileName().toString(), name(),
                        () -> Platform.runLater(() -> {
                            if (client == transfer) show("send_progress.fxml");
                        }), DesktopTransfers::updateProgress);
                Platform.runLater(() -> {
                    if (client != transfer) return;
                    client = null;
                    progress.set(1);
                    status.set("Файл доставлен и проверен: " + file.getFileName());
                });
            } catch (Exception e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                Platform.runLater(() -> {
                    if (client != transfer) return;
                    client = null;
                    show("send_progress.fxml");
                    progress.set(0);
                    status.set("Ошибка: " + e.getMessage());
                });
            }
        });
    }

    public static void updateProgress(long bytes, long total) {
        long now = System.nanoTime();
        long previous = lastProgress.get();
        if (bytes != total && now - previous < TimeUnit.MILLISECONDS.toNanos(100)) return;
        if (!lastProgress.compareAndSet(previous, now)) return;
        Platform.runLater(() -> {
            progress.set(total == 0 ? 1 : (double) bytes / total);
            status.set(bytes + " / " + total + " байт; ожидание проверки до подтверждения");
        });
    }

    public static void cancelSend() {
        if (client != null) { client.close(); client = null; }
        if (sending != null) { sending.cancel(true); sending = null; }
    }

    public static void cancelReceive() { waiting = false; node.cancelIncoming(); }

    public static void show(String fxml) {
        try { ViewController.switchScene(fxml); }
        catch (IOException e) { ViewController.showError(e.getMessage()); }
    }

    public static void close() {
        cancelSend();
        if (node != null) node.close();
        worker.shutdownNow();
    }
}
