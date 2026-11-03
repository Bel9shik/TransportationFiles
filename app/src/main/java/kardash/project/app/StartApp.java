package kardash.project.app;

import javafx.application.Application;
import javafx.stage.Stage;
import javafx.scene.image.Image;
import kardash.project.app.core.cotrollers.view.ViewController;

public class StartApp extends Application {
    @Override public void init() throws Exception { DesktopTransfers.start(); }

    @Override public void start(Stage stage) throws Exception {
        ViewController.setStage(stage);
        stage.setTitle("TransportationFiles");
        stage.getIcons().add(new Image(StartApp.class.getResource("/icons/app-icon.png").toExternalForm()));
        stage.setMinWidth(760);
        stage.setMinHeight(650);
        ViewController.switchScene("main.fxml");
        stage.show();
        String probe = System.getenv("TRANSPORT_STARTUP_PROBE");
        if (probe != null) new javafx.animation.AnimationTimer() {
            private boolean painted;
            @Override public void handle(long now) {
                if (!painted) { painted = true; return; }
                stop();
                try { java.nio.file.Files.writeString(java.nio.file.Path.of(probe), "ready"); }
                catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
            }
        }.start();
    }

    @Override public void stop() { ViewController.closeController(); DesktopTransfers.close(); }

    public static void main(String[] args) { launch(args); }
}
