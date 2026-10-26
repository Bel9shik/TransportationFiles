package kardash.project.app.core.cotrollers.FXMLControllers;

import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import kardash.project.app.DesktopTransfers;
import kardash.project.app.FileClipboard;
import java.util.Objects;

public class ProgressController implements AutoCloseable {
    @FXML private VBox transferCard;
    @FXML private Label titleLabel;
    @FXML private Label phaseLabel;
    @FXML private Label percentLabel;
    @FXML private ProgressBar progressBar;
    @FXML private Label statusLabel;
    @FXML private ImageView transferImage;
    @FXML private Button backButton;
    @FXML private Button copyButton;
    private String activeTitle;
    private final ChangeListener<DesktopTransfers.State> stateChanged = (observable, old, value) -> renderState();
    private final ChangeListener<Number> progressChanged = (observable, old, value) -> renderProgress();

    @FXML public void initialize() {
        activeTitle = titleLabel.getText();
        progressBar.progressProperty().bind(DesktopTransfers.progress);
        statusLabel.textProperty().bind(DesktopTransfers.status);
        DesktopTransfers.state.addListener(stateChanged);
        DesktopTransfers.progress.addListener(progressChanged);
        renderState();
    }

    private void renderState() {
        var state = DesktopTransfers.state.get();
        boolean receiving = "receive".equals(transferCard.getUserData());
        copyButton.setVisible(state == DesktopTransfers.State.SUCCEEDED && !DesktopTransfers.completedFiles.isEmpty());
        copyButton.setManaged(copyButton.isVisible());
        String image;
        switch (state) {
            case SUCCEEDED -> {
                titleLabel.setText(DesktopTransfers.completedFiles.size() > 1 ? (receiving ? "Файлы получены" : "Файлы отправлены") : (receiving ? "Файл получен" : "Файл отправлен"));
                phaseLabel.setText("Передача завершена · целостность проверена");
                backButton.setText("Готово — в меню");
                image = "check.png";
            }
            case FAILED -> {
                titleLabel.setText("Передача не завершена");
                phaseLabel.setText("Не удалось завершить передачу. Подробности ниже.");
                backButton.setText("Вернуться в меню");
                image = "cancel.png";
            }
            default -> {
                titleLabel.setText(activeTitle);
                phaseLabel.setText("Сохраняйте соединение до подтверждения результата");
                backButton.setText("Отменить передачу");
                image = "transfer_active.gif";
            }
        }
        transferImage.setFitWidth(state == DesktopTransfers.State.ACTIVE ? 200 : 96);
        transferImage.setFitHeight(state == DesktopTransfers.State.ACTIVE ? 200 : 96);
        transferImage.setImage(new Image(Objects.requireNonNull(getClass().getResource("/icons/" + image)).toExternalForm()));
        renderProgress();
    }

    private void renderProgress() {
        double progress = DesktopTransfers.progress.get();
        percentLabel.setText(switch (DesktopTransfers.state.get()) {
            case SUCCEEDED -> "Готово";
            case FAILED -> "Ошибка";
            case ACTIVE -> progress < 0 ? "Ожидание данных…" : progress >= 1 ? "Проверяем файл…" : String.format("%.0f%%", progress * 100);
        });
    }

    @FXML public void copyFiles() { FileClipboard.choose(DesktopTransfers.completedFiles, DesktopTransfers.status::set); }

    @FXML public void handleBack() {
        DesktopTransfers.cancelSend();
        DesktopTransfers.cancelReceive();
        DesktopTransfers.show("main.fxml");
    }

    @Override public void close() {
        DesktopTransfers.state.removeListener(stateChanged);
        DesktopTransfers.progress.removeListener(progressChanged);
        progressBar.progressProperty().unbind();
        statusLabel.textProperty().unbind();
        transferImage.setImage(null);
    }
}
