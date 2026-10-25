package kardash.project.app.core.cotrollers.FXMLControllers;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import kardash.project.app.DesktopTransfers;
import kardash.project.app.core.cotrollers.view.ViewController;
import kardash.project.app.core.services.UserDiscoveryService;
import kardash.project.transfer.Discovery;

public class WaitingForRequestController implements AutoCloseable {
    @FXML private Label ipLabel;
    @FXML private Label portLabel;
    private UserDiscoveryService discovery;

    @FXML public void initialize() {
        DesktopTransfers.waiting = true;
        ipLabel.setText("Ваш IP: " + Discovery.addresses());
        portLabel.setText("Порт: " + DesktopTransfers.port());
        discovery = new UserDiscoveryService();
        discovery.setOnFailed(event -> portLabel.setText("Порт: " + DesktopTransfers.port() + "; используйте ручной адрес"));
        discovery.start();
    }

    @FXML public void handleBack() {
        DesktopTransfers.waiting = false;
        DesktopTransfers.show("main.fxml");
    }

    @Override public void close() {
        DesktopTransfers.waiting = false;
        if (discovery != null) discovery.cancel();
    }
}
