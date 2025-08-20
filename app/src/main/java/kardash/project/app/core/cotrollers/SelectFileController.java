package kardash.project.app.core.cotrollers;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.FileChooser;
import kardash.project.app.core.cotrollers.view.ViewController;
import kardash.project.app.models.TransferContext;

import java.io.File;
import java.io.IOException;

public class SelectFileController {

    @FXML
    protected Button sendBtn;

    @FXML
    protected Button browseBtn;

    @FXML
    protected Label fileNameLabel;

    @FXML
    protected Label fileSizeLabel;

    @FXML
    public void handleBackToMainMenu() {

        TransferContext.clearFile();

        try {
            ViewController.switchScene("main.fxml");
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @FXML
    public void handleSendBtn() {

        try {
            ViewController.switchScene("select_user.fxml");
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @FXML
    public void handleBrowseBtn() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Выберите файл для отправки");

        File file = fileChooser.showOpenDialog(browseBtn.getScene().getWindow());

        if (file != null) {
            TransferContext.setFile(file);
            fileNameLabel.setText(file.getName());
            fileSizeLabel.setText("Размер: " + formatFileSize(file.length()));
            sendBtn.setDisable(false);
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes <= 0) return "0 Б";

        final String[] units = {"Б", "КБ", "МБ", "ГБ"};
        int unitIndex = 0;
        double size = bytes;
        final double factor = 1000;

        while (size >= factor && unitIndex < units.length - 1) {
            size /= factor;
            unitIndex++;
        }

        if (size == Math.floor(size)) {
            return String.format("%.0f %s", size, units[unitIndex]);
        } else {
            return String.format("%.2f %s", size, units[unitIndex]).replaceAll("\\.?0+$", "");
        }
    }
}
