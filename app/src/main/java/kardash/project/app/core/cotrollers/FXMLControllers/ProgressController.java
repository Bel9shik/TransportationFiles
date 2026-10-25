package kardash.project.app.core.cotrollers.FXMLControllers;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import kardash.project.app.DesktopTransfers;

public class ProgressController implements AutoCloseable {
    @FXML private ProgressBar progressBar;
    @FXML private Label statusLabel;
    @FXML public void initialize() {
        progressBar.progressProperty().bind(DesktopTransfers.progress);
        statusLabel.textProperty().bind(DesktopTransfers.status);
    }
    @FXML public void handleBack() {
        DesktopTransfers.cancelSend();
        DesktopTransfers.cancelReceive();
        DesktopTransfers.show("main.fxml");
    }
    @Override public void close() {
        progressBar.progressProperty().unbind();
        statusLabel.textProperty().unbind();
    }
}
