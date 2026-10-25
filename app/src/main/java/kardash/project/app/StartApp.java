package kardash.project.app;

import javafx.application.Application;
import javafx.stage.Stage;
import kardash.project.app.core.cotrollers.view.ViewController;

public class StartApp extends Application {
    @Override public void init() throws Exception { DesktopTransfers.start(); }

    @Override public void start(Stage stage) throws Exception {
        ViewController.setStage(stage);
        stage.setTitle("TransportationFiles");
        stage.setMinWidth(760);
        stage.setMinHeight(650);
        ViewController.switchScene("main.fxml");
        stage.show();
    }

    @Override public void stop() { ViewController.closeController(); DesktopTransfers.close(); }

    public static void main(String[] args) { launch(args); }
}
