package kardash.project.app.core.cotrollers;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import kardash.project.app.core.cotrollers.view.ViewController;
import kardash.project.app.core.services.UserDiscoveryService;

import java.io.IOException;

public class WaitingForRequestController {

    @FXML
    private Button backButton;

    private UserDiscoveryService discoveryService;

    @FXML
    public void initialize() {
        discoveryService = new UserDiscoveryService();
        discoveryService.setOnSucceeded(e -> {
            ViewController.showError("Уведомление типа согласие на сопряжение");
        });

        ViewController.getPrimaryStage().setOnCloseRequest(e -> discoveryService.cancel());

        discoveryService.start();
    }

    @FXML
    public void handleBack() {

        // Остановка сервиса при возврате
        if (discoveryService != null) {
            discoveryService.cancel();
        }

        try {
            ViewController.switchScene("main.fxml");
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
