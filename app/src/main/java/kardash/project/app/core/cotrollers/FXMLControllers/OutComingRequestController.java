package kardash.project.app.core.cotrollers.FXMLControllers;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import kardash.project.app.DesktopTransfers;
import kardash.project.app.models.TransferContext;

public class OutComingRequestController {
    @FXML private Label userNameLabel;
    @FXML public void initialize() {
        userNameLabel.setText(TransferContext.getAnotherUser().hostName());
        DesktopTransfers.send();
    }
    @FXML public void handleCancel() {
        DesktopTransfers.cancelSend();
        DesktopTransfers.show("select_user.fxml");
    }
}
