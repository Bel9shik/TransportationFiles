package kardash.project.app.core.cotrollers.view;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import lombok.Getter;

import java.io.IOException;

public class ViewController {
    @Getter
    private static Stage primaryStage;

    public static void setStage(Stage stage) {
        if (primaryStage == null) primaryStage = stage;
    }

    private static Object controller;

    public static void switchScene(String fxmlFile) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(ViewController.class.getResource("/FXML/" + fxmlFile));
        closeController();
        var root = (javafx.scene.Parent) fxmlLoader.load();
        controller = fxmlLoader.getController();
        if (primaryStage.getScene() == null) primaryStage.setScene(new Scene(root, 800, 650));
        else primaryStage.getScene().setRoot(root);
    }

    public static void closeController() {
        if (controller instanceof AutoCloseable closeable) {
            try { closeable.close(); }
            catch (Exception e) { e.printStackTrace(); }
        }
        controller = null;
    }

    public static void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Ошибка");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void showCompletion(String title, String header, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(primaryStage);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.show();
    }

    public static void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Информация");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}