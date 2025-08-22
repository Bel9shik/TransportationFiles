package kardash.project.app.core.cotrollers;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.stage.WindowEvent;
import kardash.project.app.core.cotrollers.view.ViewController;
import kardash.project.app.core.services.UserDiscoveryService;

import java.io.IOException;

public class WaitingForRequestController {

    @FXML
    private Button backButton;

    private static UserDiscoveryService discoveryService;

    @FXML
    public void initialize() {
        discoveryService = new UserDiscoveryService();

        ViewController.getPrimaryStage().addEventHandler(WindowEvent.WINDOW_CLOSE_REQUEST, e -> stop());

        discoveryService.start();
    }

    public static void incomingRequest() {

        Platform.runLater(() -> {
            try {
                ViewController.switchScene("incoming_request.fxml");
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        stop();

    }

    @FXML
    public void handleBack() {

        stop();

        try {
            ViewController.switchScene("main.fxml");
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private static void stop() {
        if (discoveryService != null) {
            Platform.runLater(() -> discoveryService.cancel());
        }
    }
}
