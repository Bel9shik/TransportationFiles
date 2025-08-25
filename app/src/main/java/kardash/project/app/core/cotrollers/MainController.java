package kardash.project.app.core.cotrollers;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import kardash.project.app.models.constants.Constants;
import kardash.project.app.core.cotrollers.files.ReceiveController;
import kardash.project.app.core.cotrollers.files.SendController;
import kardash.project.app.core.cotrollers.view.ViewController;

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
