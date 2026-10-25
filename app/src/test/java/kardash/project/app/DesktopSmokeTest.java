package kardash.project.app;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import kardash.project.app.core.cotrollers.view.ViewController;
import kardash.project.app.models.IncomingRequestContext;
import kardash.project.app.models.SendingFile;
import kardash.project.app.models.TransferContext;
import kardash.project.app.models.User;
import kardash.project.transfer.TransferNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class DesktopSmokeTest {
    @TempDir Path directory;

    @Test void loadsScreensAndSendsFromTheExistingUi() throws Exception {
        CompletableFuture<Void> ready = new CompletableFuture<>();
        Platform.startup(() -> { Platform.setImplicitExit(false); ready.complete(null); });
        ready.get(5, TimeUnit.SECONDS);
        DesktopTransfers.start();
        Path source = Files.write(directory.resolve("source"), new byte[]{1, 2, 3});
        Path destination = directory.resolve("received");
        CompletableFuture<Path> received = new CompletableFuture<>();
        try (TransferNode receiver = new TransferNode(0, new TransferNode.Listener() {
            @Override public CompletableFuture<Path> accept(TransferNode.Offer offer) {
                return CompletableFuture.completedFuture(destination);
            }
            @Override public void received(Path file) { received.complete(file); }
            @Override public void failed(String error) { received.completeExceptionally(new AssertionError(error)); }
        })) {
            fx(() -> {
                Stage stage = new Stage();
                ViewController.setStage(stage);
                for (String name : new String[]{"main.fxml", "select_file.fxml", "select_user.fxml", "waiting_for_request.fxml"}) {
                    DesktopTransfers.show(name);
                    assertNotNull(stage.getScene().getRoot());
                    stage.show();
                    assertTrue(stage.isShowing());
                }
                assertTrue(DesktopTransfers.waiting);
                TransferContext.setIncomingRequestContext(new IncomingRequestContext(
                        new TransferNode.Offer("Test", "file", 3), new CompletableFuture<>()));
                DesktopTransfers.show("incoming_request.fxml");
                assertFalse(DesktopTransfers.waiting);
                DesktopTransfers.show("receive_progress.fxml");
                TransferContext.setAnotherUser(new User("127.0.0.1", receiver.port(), "Receiver", 0));
                TransferContext.setFile(new SendingFile(source.toFile(), "3 Б"));
                DesktopTransfers.show("outcoming_request.fxml");
            });
            assertEquals(destination, received.get(10, TimeUnit.SECONDS));
            assertArrayEquals(Files.readAllBytes(source), Files.readAllBytes(destination));
            fx(() -> {
                DesktopTransfers.show("select_file.fxml");
                Button send = (Button) ViewController.getPrimaryStage().getScene().lookup("#sendBtn");
                assertNotNull(send);
                assertFalse(send.isDisabled(), "Selected file must survive returning from device selection");
            });
        } finally {
            fx(() -> { ViewController.closeController(); ViewController.getPrimaryStage().close(); DesktopTransfers.close(); });
            Platform.exit();
        }
    }

    private static void fx(Runnable action) throws Exception {
        FutureTask<Void> task = new FutureTask<>(action, null);
        Platform.runLater(task);
        task.get(10, TimeUnit.SECONDS);
    }
}
