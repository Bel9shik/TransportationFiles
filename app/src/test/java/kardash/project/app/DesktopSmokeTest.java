package kardash.project.app;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Window;
import javafx.scene.image.ImageView;
import kardash.project.transfer.TransferClient;
import javafx.stage.Stage;
import kardash.project.app.core.cotrollers.view.ViewController;
import kardash.project.app.core.cotrollers.FXMLControllers.IncomingRequestController;
import kardash.project.app.models.constants.UserConfig;
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
        CompletableFuture<Void> completed = new CompletableFuture<>();
        CompletableFuture<Void> bytesArrived = new CompletableFuture<>();
        CountDownLatch allowSave = new CountDownLatch(1);
        var preferences = java.util.prefs.Preferences.userRoot().node("transport-files-tests/" + java.util.UUID.randomUUID());
        try (TransferNode receiver = new TransferNode(0, new TransferNode.Listener() {
            @Override public CompletableFuture<Path> accept(TransferNode.Offer offer) {
                return CompletableFuture.completedFuture(destination);
            }
            @Override public void progress(long bytes, long total) {
                if (bytes != total) return;
                bytesArrived.complete(null);
                try { assertTrue(allowSave.await(10, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
            }
            @Override public void received(Path file) { received.complete(file); }
            @Override public void failed(String error) { received.completeExceptionally(new AssertionError(error)); }
        })) {
            fx(() -> {
                DesktopTransfers.status.addListener((observable, old, value) -> {
                    if (value.startsWith("Файл доставлен и проверен:")) completed.complete(null);
                });
                Stage stage = new Stage();
                try { new StartApp().start(stage); }
                catch (Exception e) { throw new AssertionError(e); }
                assertEquals(1, stage.getIcons().size());
                assertFalse(stage.getIcons().get(0).isError());
                for (String name : new String[]{"main.fxml", "select_file.fxml", "select_user.fxml", "waiting_for_request.fxml"}) {
                    DesktopTransfers.show(name);
                    assertNotNull(stage.getScene().getRoot());
                    stage.show();
                    assertTrue(stage.isShowing());
                    if (name.equals("select_user.fxml")) snapshot("discovery");
                    if (name.equals("main.fxml")) {
                        Label folder = (Label) stage.getScene().lookup("#downloadFolderLabel");
                        assertFalse(folder.getText().isBlank());
                        snapshot("download-settings");
                    }
                }
                assertTrue(DesktopTransfers.waiting);
                TransferContext.setIncomingRequestContext(new IncomingRequestContext(
                        new TransferNode.Offer("Test", "file", 3), new CompletableFuture<>()));
                DesktopTransfers.show("incoming_request.fxml");
                assertFalse(DesktopTransfers.waiting);
                assertNotNull(stage.getScene().lookup("#saveAsBtn"));
                assertFalse(((Button) stage.getScene().lookup("#acceptBtn")).isDisabled());
                assertFalse(((Label) stage.getScene().lookup("#destinationLabel")).getText().isBlank());
                snapshot("incoming-destination");
                DesktopTransfers.show("receive_progress.fxml");
                TransferContext.setAnotherUser(new User("127.0.0.1", receiver.port(), "Receiver", 0));
                TransferContext.setFile(new SendingFile(source.toFile(), "3 Б"));
                DesktopTransfers.show("outcoming_request.fxml");
            });
            bytesArrived.get(5, TimeUnit.SECONDS);
            fx(() -> {
                assertEquals(DesktopTransfers.State.ACTIVE, DesktopTransfers.state.get());
                ImageView animation = (ImageView) ViewController.getPrimaryStage().getScene().lookup("#transferImage");
                assertNotNull(animation);
                assertTrue(animation.getImage().getUrl().endsWith("transfer_active.gif"));
                assertFalse(Window.getWindows().stream().anyMatch(window -> window instanceof Stage popup
                        && "Отправка завершена".equals(popup.getTitle())));
                snapshot("send-active");
            });
            allowSave.countDown();
            assertEquals(destination, received.get(10, TimeUnit.SECONDS));
            assertArrayEquals(Files.readAllBytes(source), Files.readAllBytes(destination));
            completed.get(5, TimeUnit.SECONDS);
            fx(() -> {
                Label title = (Label) ViewController.getPrimaryStage().getScene().lookup("#titleLabel");
                assertNotNull(title, "Transfer screen needs a visible completion heading");
                assertEquals("Файл отправлен", title.getText());
                assertFalse(Window.getWindows().stream().anyMatch(window -> window instanceof Stage popup
                        && "Отправка завершена".equals(popup.getTitle())));
                snapshot("send-complete");
            });
            CompletableFuture<Void> incomingOffer = new CompletableFuture<>();
            CompletableFuture<Void> incomingDone = new CompletableFuture<>();
            fx(() -> {
                TransferContext.clearIncomingRequestContext();
                DesktopTransfers.status.addListener((observable, old, value) -> {
                    if (value.startsWith("Приём:")) incomingOffer.complete(null);
                    if (value.startsWith("Сохранено:")) incomingDone.complete(null);
                });
                DesktopTransfers.show("waiting_for_request.fxml");
            });
            Path downloads = Files.createDirectory(directory.resolve("Downloads"));
            Path existing = Files.writeString(downloads.resolve("source"), "keep this file");
            Path incomingFile = downloads.resolve("source (1)");
            UserConfig config = new UserConfig(preferences, downloads);
            CompletableFuture<Void> upload = CompletableFuture.runAsync(() -> {
                try (TransferClient client = new TransferClient("127.0.0.1", DesktopTransfers.port())) {
                    client.send(source, "source", "Test sender", () -> {}, (a, b) -> {});
                } catch (Exception e) { throw new CompletionException(e); }
            });
            incomingOffer.get(5, TimeUnit.SECONDS);
            fx(() -> {
                try {
                    ViewController.closeController();
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/FXML/incoming_request.fxml"));
                    loader.setControllerFactory(type -> new IncomingRequestController(config));
                    ViewController.getPrimaryStage().getScene().setRoot(loader.load());
                } catch (java.io.IOException e) { throw new AssertionError(e); }
                ((Button) ViewController.getPrimaryStage().getScene().lookup("#acceptBtn")).fire();
                snapshot("receive-active");
            });
            upload.get(5, TimeUnit.SECONDS);
            incomingDone.get(5, TimeUnit.SECONDS);
            fx(() -> {
                assertEquals(DesktopTransfers.State.SUCCEEDED, DesktopTransfers.state.get());
                Label title = (Label) ViewController.getPrimaryStage().getScene().lookup("#titleLabel");
                assertEquals("Файл получен", title.getText());
                assertFalse(Window.getWindows().stream().anyMatch(window -> window instanceof Stage popup
                        && "Приём завершён".equals(popup.getTitle())));
                snapshot("receive-complete");
            });
            assertArrayEquals(Files.readAllBytes(source), Files.readAllBytes(incomingFile));
            assertEquals("keep this file", Files.readString(existing));
            fx(() -> {
                DesktopTransfers.show("select_file.fxml");
                Button send = (Button) ViewController.getPrimaryStage().getScene().lookup("#sendBtn");
                assertNotNull(send);
                assertFalse(send.isDisabled(), "Selected file must survive returning from device selection");
            });
        } finally {
            allowSave.countDown();
            fx(() -> { ViewController.closeController(); ViewController.getPrimaryStage().close(); DesktopTransfers.close(); });
            Platform.exit();
            preferences.removeNode();
            preferences.flush();
        }
    }

    private static void snapshot(String name) {
        try {
            var image = ViewController.getPrimaryStage().getScene().snapshot(null);
            int width = (int) image.getWidth(), height = (int) image.getHeight();
            int[] pixels = new int[width * height];
            image.getPixelReader().getPixels(0, 0, width, height,
                    javafx.scene.image.PixelFormat.getIntArgbInstance(), pixels, 0, width);
            var png = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            png.setRGB(0, 0, width, height, pixels, 0, width);
            Path output = Path.of("build", "reports", "screenshots", name + ".png");
            Files.createDirectories(output.getParent());
            javax.imageio.ImageIO.write(png, "png", output.toFile());
        } catch (java.io.IOException e) { throw new AssertionError(e); }
    }

    private static void fx(Runnable action) throws Exception {
        FutureTask<Void> task = new FutureTask<>(action, null);
        Platform.runLater(task);
        task.get(10, TimeUnit.SECONDS);
    }
}
