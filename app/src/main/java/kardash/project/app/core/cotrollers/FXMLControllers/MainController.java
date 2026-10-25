package kardash.project.app.core.cotrollers.FXMLControllers;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.DirectoryChooser;
import kardash.project.app.models.constants.UserConfig;
import java.nio.file.Files;
import kardash.project.app.core.cotrollers.view.ViewController;

import java.io.IOException;

public class MainController {

    @FXML
    Button receiveBtn;

    @FXML
    Button sendBtn;

    @FXML private Label downloadFolderLabel;

    @FXML public void initialize() { refreshFolder(); }

    private void refreshFolder() {
        downloadFolderLabel.setText(UserConfig.getInstance().downloadDirectory().toString());
    }

    @FXML private void chooseDownloadFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Папка для полученных файлов");
        var current = UserConfig.getInstance().downloadDirectory();
        if (Files.isDirectory(current)) chooser.setInitialDirectory(current.toFile());
        var selected = chooser.showDialog(ViewController.getPrimaryStage());
        if (selected == null) return;
        try {
            UserConfig.getInstance().setDownloadDirectory(selected.toPath());
            refreshFolder();
        } catch (Exception e) { ViewController.showError("Не удалось сохранить настройку: " + e.getMessage()); }
    }

    @FXML private void resetDownloadFolder() {
        try {
            UserConfig.getInstance().resetDownloadDirectory();
            refreshFolder();
        } catch (Exception e) { ViewController.showError("Не удалось сохранить настройку: " + e.getMessage()); }
    }


    @FXML
    public void sendFile() {
        try {
            ViewController.switchScene("select_file.fxml");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void receiveFile() {

        try {
            ViewController.switchScene("waiting_for_request.fxml");
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


}
