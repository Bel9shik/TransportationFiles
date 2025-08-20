package kardash.project.app.cotrollers;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import kardash.project.app.cotrollers.view.ViewController;

import java.io.IOException;

public class WaitingForRequestController {

    @FXML
    private Button backButton;

    private UserDiscoveryService discoveryService;

    @FXML
    public void initialize() {
        discoveryService = new UserDiscoveryService();
//        discoveryService.setOnSucceeded(e -> users.setAll(discoveryService.getValue()));
        discoveryService.setOnSucceeded(e -> {
//            userListView.setItems(discoveryService.getValue());
            showError("Уведомление типа согласие на сопряжение");
        });

        ViewController.getPrimaryStage().setOnCloseRequest(e -> discoveryService.cancel());

        discoveryService.start();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Ошибка");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
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
