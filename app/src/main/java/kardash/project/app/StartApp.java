package kardash.project.app;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import kardash.project.app.models.constants.Constants;
import kardash.project.app.core.cotrollers.view.ViewController;
import kardash.project.app.grpc.GRPCServer;

import java.util.Objects;

public class StartApp extends Application {

    private GRPCServer grpcServer;
    private Thread grpcThread;

    @Override
    public void start(Stage primaryStage) throws Exception {

        grpcServer = new GRPCServer(Constants.GRPC_PORT);
        grpcThread = new Thread(grpcServer);
        grpcThread.start();

        Parent root = FXMLLoader.load(Objects.requireNonNull(getClass().getResource("/FXML/main.fxml")));
        primaryStage.setTitle("File Transfer");
        primaryStage.addEventHandler(WindowEvent.WINDOW_CLOSE_REQUEST, event -> stopApp(primaryStage));
        ViewController.setStage(primaryStage);
        primaryStage.setScene(new Scene(root, 800, 600));
        primaryStage.show();
    }

    private void stopApp(Stage primaryStage) {
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