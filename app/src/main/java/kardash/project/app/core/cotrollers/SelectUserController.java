package kardash.project.app.core.cotrollers;

import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.WindowEvent;
import kardash.project.app.core.cotrollers.view.ViewController;
import kardash.project.app.core.services.PairingService;
import kardash.project.app.core.services.UserDiscoveryService;
import kardash.project.app.models.TransferContext;
import kardash.project.app.models.User;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Comparator;
import java.util.Objects;


public class SelectUserController {
    @FXML
    private ListView<User> userListView;
    @FXML
    private ProgressIndicator loadingIndicator;
    @FXML
    private Button backBtn;
    @FXML
    private Button continueBtn;

    // Сервис для обнаружения пользователей
    private UserDiscoveryService discoveryService;
    private ObservableList<User> users;

    @FXML
    public void initialize() {
        discoveryService = new UserDiscoveryService();
        users = discoveryService.getUsers();
//        userListView.setItems(users);
        SortedList<User> sorted = new SortedList<>(users);
        sorted.setComparator(Comparator.comparing(User::hostName));
        userListView.setItems(sorted);
        userListView.setCellFactory(this::createUserCell);
        discoveryService.setOnFailed(e -> {
            loadingIndicator.setVisible(false);
            showError("Ошибка обнаружения пользователей");
        });

        discoveryService.setOnCancelled(e -> {
            loadingIndicator.setVisible(false);
        });
        ViewController.getPrimaryStage().addEventHandler(WindowEvent.WINDOW_CLOSE_REQUEST, event -> discoveryService.cancel());
        continueBtn.disableProperty().bind(
                userListView.getSelectionModel().selectedItemProperty().isNull()
        );

        startUserDiscovery();
    }

    private void startUserDiscovery() {
        loadingIndicator.setVisible(true);
        users.clear();

        // Обновление списка при обнаружении новых пользователей
        discoveryService.start();
    }

    // Создание кастомной ячейки для пользователя
    private ListCell<User> createUserCell(ListView<User> param) {
        return new ListCell<>() {
            private final HBox root = new HBox(10);
            private final ImageView avatar = new ImageView();
            private final Label nameLabel = new Label();
            private final Label ipLabel = new Label();

            {
                root.setAlignment(Pos.CENTER_LEFT);
                root.setPadding(new Insets(5));

                avatar.setFitWidth(40);
                avatar.setFitHeight(40);
                avatar.setImage(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icons/user_icon.png"))));

                VBox infoBox = new VBox(2);
                nameLabel.setStyle("-fx-text-fill: black; -fx-font-size: 14;");
                ipLabel.setStyle("-fx-text-fill: #676262; -fx-font-size: 12;");
                infoBox.getChildren().addAll(nameLabel, ipLabel);

                root.getChildren().addAll(avatar, infoBox);
            }

            @Override
            protected void updateItem(User user, boolean empty) {
                super.updateItem(user, empty);
                if (empty || user == null) {
                    setGraphic(null);
                } else {
                    nameLabel.setText(user.hostName());
                    ipLabel.setText(user.ip() + ":" + user.port());
                    setGraphic(root);
                }
            }
        };
    }

    @FXML
    private void handleContinue() {
        User selected = userListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            if (discoveryService != null) {
                discoveryService.succeeded();
            }
            try {
                TransferContext.setUser(selected);
                ViewController.switchScene("outcoming_request.fxml");

                PairingService pairingService = new PairingService(selected.hostName(), InetAddress.getByName(selected.ip()));
                pairingService.setOnSucceeded(e -> {
                    boolean ok = pairingService.getValue();
                    try {
                        if (ok) ViewController.switchScene("send_progress.fxml");
                        else {
                            showError("Пользователь отклонил запрос");
                            TransferContext.clearUser();
                            ViewController.switchScene("select_user.fxml");
                        }
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }

                });

                pairingService.setOnFailed(e -> {
                    showError("Ошибка соединения: " + pairingService.getException().getMessage());
                    try {
                        TransferContext.clearUser();
                        ViewController.switchScene("select_user.fxml");
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                });
                pairingService.start();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @FXML
    private void handleBack() {
        // Остановка сервиса при возврате
        if (discoveryService != null) {
            discoveryService.cancel();
        }

        // Возврат на предыдущий экран
        try {
            ViewController.switchScene("main.fxml");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Ошибка");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

}
