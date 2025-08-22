package kardash.project.app.core.cotrollers;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import kardash.project.app.core.cotrollers.view.ViewController;
import kardash.project.app.models.IncomingRequestContext;
import kardash.project.app.models.TransferContext;

import java.io.IOException;

public class IncomingRequestController {

    @FXML
    protected Label senderLabel;

    @FXML
    protected Label fileLabel;

    @FXML
    protected Label sizeLabel;

    @FXML
    protected Button rejectBtn;

    @FXML
    protected Button acceptBtn;

    private IncomingRequestContext context;

    @FXML
    public void initialize() {
        context = TransferContext.getIncomingRequestContext();

        senderLabel.setText("Отправитель: " + context.getPCName());
        fileLabel.setText("Файл: " + context.getFileName());
        sizeLabel.setText("Размер: " + context.getFileSize());
    }

    @FXML
    private void accept() {
        context.answer.complete(true);
        clean();
        try {
            ViewController.switchScene("receive_progress.fxml");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void reject() {
        context.answer.complete(false);
        clean();
        try {
            ViewController.switchScene("waiting_for_request.fxml");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void clean() {
        TransferContext.clearIncomingRequestContext();
    }

}
