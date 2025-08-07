package org.example;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.constants.Constants;
import org.example.cotrollers.MulticastController;
import org.example.cotrollers.view.SceneController;
import org.example.grpc.GRPCServer;
import org.example.models.ApplicationModel;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class StartApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        Parent root = FXMLLoader.load(Objects.requireNonNull(getClass().getResource("/FXML/main.fxml")));
        primaryStage.setTitle("File Transfer");
        SceneController.setStage(primaryStage);
        primaryStage.setScene(new Scene(root, 800, 600));
        primaryStage.show();
    }

    public static void main(String[] args) throws IOException {

        ConcurrentHashMap<InetAddress, String> map = new ConcurrentHashMap<>();

        ApplicationModel applicationModel = new ApplicationModel(map);

        MulticastController multicastController = new MulticastController(applicationModel.getUsers());
        multicastController.startDiscovery();

        GRPCServer server = new GRPCServer(Constants.GRPC_PORT);
        Thread serverThread = new Thread(server);
        serverThread.start();

        launch(args);
    }

}