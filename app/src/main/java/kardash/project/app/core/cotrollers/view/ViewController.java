package kardash.project.app.cotrollers.view;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import lombok.Getter;

import java.io.IOException;

public class ViewController {
    @Getter
    private static Stage primaryStage;

    public static void setStage(Stage stage) {
        if (primaryStage == null) primaryStage = stage;
    }

    public static void switchScene(String fxmlFile) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(ViewController.class.getResource("/FXML/" + fxmlFile));
        Scene scene = new Scene(fxmlLoader.load());
        primaryStage.setScene(scene);
    }
}