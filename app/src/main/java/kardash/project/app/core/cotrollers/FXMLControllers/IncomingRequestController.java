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

import java.io.File;

public class IncomingRequestController {
    @FXML private Label senderLabel;
    @FXML private Label fileLabel;
    @FXML private Label sizeLabel;
    @FXML private Button acceptBtn;
    private IncomingRequestContext context;

    @FXML public void initialize() {
        context = TransferContext.getIncomingRequestContext();
        senderLabel.setText("Отправитель: " + context.offer().sender());
        fileLabel.setText("Файл: " + context.offer().filename());
        sizeLabel.setText("Размер: " + SelectFileController.formatFileSize(context.offer().size()));
        context.answer().whenComplete((path, error) -> Platform.runLater(() -> {
            acceptBtn.setDisable(true);
            if (path == null) sizeLabel.setText("Запрос отклонён, отменён или истёк");
        }));
    }

    @FXML private void accept() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Сохранить полученный файл");
        chooser.setInitialFileName(context.offer().filename());
        File target = chooser.showSaveDialog(ViewController.getPrimaryStage());
        if (target == null) return;
        if (target.exists()) {
            ViewController.showError("Выберите новое имя: существующие файлы не перезаписываются");
            return;
        }
        if (context.answer().isDone()) {
            ViewController.showError("Запрос уже отменён или истёк");
            return;
        }
        DesktopTransfers.show("receive_progress.fxml");
        context.answer().complete(target.toPath());
    }

    @FXML private void reject() {
        context.answer().complete(null);
        TransferContext.clearIncomingRequestContext();
        DesktopTransfers.show("waiting_for_request.fxml");
    }
}
