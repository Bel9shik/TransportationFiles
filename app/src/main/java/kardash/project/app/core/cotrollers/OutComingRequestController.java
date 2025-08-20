package kardash.project.app.cotrollers;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import kardash.project.app.cotrollers.view.ViewController;
import kardash.project.app.models.TransferContext;
import kardash.project.app.models.User;

public class OutComingRequestController {

    private User user;

    @FXML
    protected Label userNameLabel;

    @FXML
    protected Button cancelButton;

    @FXML
    public void initialize() {
        user = TransferContext.getUser();
        TransferContext.clear();

        userNameLabel.setText(user.hostName());
    }

    @FXML
    public void handleCancel() {
        try {
            ViewController.switchScene("select_user.fxml");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
