package org.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.constants.Constants;
import org.example.cotrollers.MulticastController;
import org.example.cotrollers.view.SceneController;
import org.example.grpc.GRPCServer;
import org.example.models.ApplicationModel;

import java.util.Objects;

public class StartApp extends Application {

    private ApplicationModel applicationModel;
    private MulticastController multicastController;
    private GRPCServer grpcServer;
    private Thread grpcThread;

    @Override
    public void start(Stage primaryStage) throws Exception {
        ApplicationModel applicationModel = new ApplicationModel();

        multicastController = new MulticastController(applicationModel.getUsers());
        multicastController.startDiscovery();

        grpcServer = new GRPCServer(Constants.GRPC_PORT);
        grpcThread = new Thread(grpcServer);
        grpcThread.start();

        Parent root = FXMLLoader.load(Objects.requireNonNull(getClass().getResource("/FXML/main.fxml")));
        primaryStage.setTitle("File Transfer");
        primaryStage.setOnCloseRequest(event -> stopApp(primaryStage));
        SceneController.setStage(primaryStage);
        primaryStage.setScene(new Scene(root, 800, 600));
        primaryStage.show();
    }

    private void stopApp(Stage primaryStage) {
        multicastController.stopDiscovery();
        grpcServer.stop();
        try {
            grpcThread.join(2000); // ждать до 2 секунд
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        primaryStage.close();
        Platform.exit();
        System.exit(0);
    }

    public static void main(String[] args) {
        launch(args);
    }

}