package kardash.project.app.cotrollers;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import kardash.project.app.constants.Constants;
import kardash.project.app.cotrollers.files.ReceiveController;
import kardash.project.app.cotrollers.files.SendController;
import kardash.project.app.cotrollers.view.ViewController;

import java.io.IOException;

public class MainController {

    @FXML
    Button receiveBtn;

    @FXML
    Button sendBtn;

    private final ReceiveController receiveController;
    private final SendController sendController;

    public MainController() {
        receiveController = new ReceiveController();
        sendController = new SendController();
    }

    public void sendFile(String serverIP, String filePath) {

        sendController.sendFile(serverIP, Constants.GRPC_PORT, filePath);

    }

    @FXML
    public void sendFile() {
        try {
            ViewController.switchScene("select_file.fxml");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void receiveFile() {

        try {
            ViewController.switchScene("waiting_for_request.fxml");
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


}
