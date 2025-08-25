package kardash.project.app.core.cotrollers;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.WindowEvent;
import kardash.project.app.core.cotrollers.view.ViewController;
import kardash.project.app.core.services.UserDiscoveryService;
import kardash.project.app.models.TransferContext;

import java.io.IOException;

public class WaitingForRequestController {

    private static UserDiscoveryService discoveryService;

    @FXML
    protected Label ipLabel;

    @FXML
    protected Label portLabel;

    @FXML
    public void initialize() {
        discoveryService = new UserDiscoveryService();

        ViewController.getPrimaryStage().addEventHandler(WindowEvent.WINDOW_CLOSE_REQUEST, e -> stop());

        ipLabel.setText("Ваш IP: " + TransferContext.getMeUser().ip());
        portLabel.setText("Порт: " + TransferContext.getMeUser().port());

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
