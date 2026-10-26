package kardash.project.app.core.cotrollers.FXMLControllers;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.FileChooser;
import kardash.project.app.core.cotrollers.view.ViewController;
import kardash.project.app.models.SendingFile;
import kardash.project.app.models.TransferContext;

import java.io.File;
import java.io.IOException;
import java.util.List;
import kardash.project.app.FileClipboard;

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
    public void initialize() {
        SendingFile selected = TransferContext.getFile();
        if (selected != null) {
            fileNameLabel.setText(selected.description());
            fileSizeLabel.setText("Размер: " + selected.stringSize());
            sendBtn.setDisable(false);
        }
    }

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
        fileChooser.setTitle("Выберите файлы для отправки");

        List<File> files = fileChooser.showOpenMultipleDialog(browseBtn.getScene().getWindow());

        if (files != null && !files.isEmpty()) {
            if (files.size() > 200) { ViewController.showError("Выберите не больше 200 файлов"); return; }
            String stringSize = formatFileSize(files.stream().mapToLong(File::length).sum());
            TransferContext.setFile(new SendingFile(files, stringSize));
            fileNameLabel.setText(TransferContext.getFile().description());
            fileSizeLabel.setText("Размер: " + stringSize);
            sendBtn.setDisable(false);
        }
    }

    @FXML public void copyFiles() {
        SendingFile selected = TransferContext.getFile();
        if (selected == null) return;
        FileClipboard.choose(selected.files().stream().map(File::toPath).toList(), fileSizeLabel::setText);
    }

    public static String formatFileSize(long bytes) {
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
