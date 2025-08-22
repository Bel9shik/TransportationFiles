package kardash.project.app.core.cotrollers;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import kardash.project.app.core.cotrollers.view.ViewController;
import kardash.project.app.core.services.CancelService;
import kardash.project.app.models.TransferContext;
import kardash.project.app.models.User;

import java.io.IOException;

public class OutComingRequestController {

    private User user;

    @FXML
    protected Label userNameLabel;

    @FXML
    protected Button cancelButton;

    @FXML
    public void initialize() {
        user = TransferContext.getUser();
        TransferContext.clearUser();

        userNameLabel.setText(user.hostName());
    }

    @FXML
    public void handleCancel() {

        CancelService.callAsync(user.ip(), user.port());

        try {
            ViewController.switchScene("select_user.fxml");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
