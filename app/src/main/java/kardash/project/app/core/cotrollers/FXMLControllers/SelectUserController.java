package kardash.project.app.core.cotrollers.FXMLControllers;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import kardash.project.app.DesktopTransfers;
import kardash.project.app.core.cotrollers.view.ViewController;
import kardash.project.app.core.services.UserDiscoveryService;
import kardash.project.app.models.TransferContext;
import kardash.project.app.models.User;
import kardash.project.transfer.Discovery;

public class SelectUserController implements AutoCloseable {
    @FXML private ListView<User> userListView;
    @FXML private ProgressIndicator loadingIndicator;
    @FXML private Button continueBtn;
    @FXML private TextField addressField;
    @FXML private Label discoveryStatus;
    private UserDiscoveryService discovery;

    @FXML public void initialize() {
        discovery = new UserDiscoveryService();
        userListView.setItems(discovery.getUsers());
        userListView.setCellFactory(view -> new ListCell<>() {
            @Override protected void updateItem(User user, boolean empty) {
                super.updateItem(user, empty);
                setText(empty || user == null ? null : user.hostName() + " — " + user.ip() + ":" + user.port());
            }
        });
        discovery.setOnFailed(event -> {
            loadingIndicator.setVisible(false);
            discoveryStatus.setText("Автопоиск недоступен. Введите IP:порт с экрана получателя.");
        });
        discovery.messageProperty().addListener((observable, old, message) -> discoveryStatus.setText(message));
        continueBtn.disableProperty().bind(userListView.getSelectionModel().selectedItemProperty().isNull()
                .and(addressField.textProperty().isEmpty()));
        discovery.start();
    }

    @FXML private void handleContinue() {
        if (TransferContext.getFile() == null) { DesktopTransfers.show("select_file.fxml"); return; }
        try {
            User selected = userListView.getSelectionModel().getSelectedItem();
            if (!addressField.getText().isBlank()) {
                var peer = Discovery.endpoint(addressField.getText());
                selected = new User(peer.host(), peer.port(), peer.name(), System.currentTimeMillis());
            }
            if (selected == null) return;
            TransferContext.setAnotherUser(selected);
            DesktopTransfers.show("outcoming_request.fxml");
        } catch (IllegalArgumentException e) { ViewController.showError(e.getMessage()); }
    }

    @FXML private void handleBack() { DesktopTransfers.show("select_file.fxml"); }
    @Override public void close() { if (discovery != null) discovery.cancel(); }
}
