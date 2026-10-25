package kardash.project.app.core.cotrollers.FXMLControllers;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.FileChooser;
import kardash.project.app.DesktopTransfers;
import kardash.project.app.core.cotrollers.view.ViewController;
import kardash.project.app.models.IncomingRequestContext;
import kardash.project.app.models.TransferContext;
import kardash.project.app.models.constants.UserConfig;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

public class IncomingRequestController {
    @FXML private Label senderLabel;
    @FXML private Label fileLabel;
    @FXML private Label sizeLabel;
    @FXML private Button acceptBtn;
    @FXML private Button saveAsBtn;
    @FXML private Label destinationLabel;
    private IncomingRequestContext context;
    private final UserConfig config;

    public IncomingRequestController() { this(UserConfig.getInstance()); }
    public IncomingRequestController(UserConfig config) { this.config = config; }

    @FXML public void initialize() {
        context = TransferContext.getIncomingRequestContext();
        senderLabel.setText("Отправитель: " + context.offer().sender());
        fileLabel.setText("Файл: " + context.offer().filename());
        sizeLabel.setText("Размер: " + SelectFileController.formatFileSize(context.offer().size()));
        destinationLabel.setText("Сохранить в: " + config.downloadDirectory());
        context.answer().whenComplete((path, error) -> Platform.runLater(() -> {
            acceptBtn.setDisable(true);
            saveAsBtn.setDisable(true);
            if (path == null) sizeLabel.setText("Запрос отклонён, отменён или истёк");
        }));
    }

    @FXML private void accept() {
        if (context.answer().isDone()) return;
        try { receive(config.targetFor(context.offer().filename())); }
        catch (Exception e) { ViewController.showError(e.getMessage()); }
    }

    @FXML private void saveAs() {
        if (context.answer().isDone()) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Сохранить полученный файл");
        chooser.setInitialFileName(context.offer().filename());
        Path directory = config.downloadDirectory();
        if (Files.isDirectory(directory)) chooser.setInitialDirectory(directory.toFile());
        File target = chooser.showSaveDialog(ViewController.getPrimaryStage());
        if (target == null) return;
        receive(target.toPath());
    }

    private void receive(Path target) {
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            ViewController.showError("Выберите новое имя: существующие файлы не перезаписываются");
            return;
        }
        if (context.answer().isDone()) {
            ViewController.showError("Запрос уже отменён или истёк");
            return;
        }
        DesktopTransfers.show("receive_progress.fxml");
        context.answer().complete(target);
    }

    @FXML private void reject() {
        context.answer().complete(null);
        TransferContext.clearIncomingRequestContext();
        DesktopTransfers.show("waiting_for_request.fxml");
    }
}
