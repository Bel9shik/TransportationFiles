package kardash.project.app;

import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.image.PixelFormat;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.VBox;
import kardash.project.app.core.cotrollers.view.ViewController;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class FileClipboard {
    private FileClipboard() {}

    public static void choose(List<Path> files, Consumer<String> feedback) {
        if (files.isEmpty()) return;
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(ViewController.getPrimaryStage());
        dialog.setTitle("В буфер обмена");
        VBox content = new VBox(10);
        Label hint = new Label("Отметьте файлы. Одна фотография копируется как изображение; несколько файлов — списком.");
        hint.setWrapText(true);
        content.getChildren().add(hint);
        List<CheckBox> choices = new ArrayList<>();
        VBox rows = new VBox(8);
        for (Path file : files) {
            CheckBox choice = new CheckBox(file.getFileName().toString());
            choice.setSelected(true);
            choices.add(choice);
            rows.getChildren().add(choice);
        }
        ScrollPane scroll = new ScrollPane(rows);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(Math.min(240, files.size() * 32));
        content.getChildren().add(scroll);
        Button all = new Button("Выбрать все");
        all.setOnAction(event -> choices.forEach(choice -> choice.setSelected(true)));
        content.getChildren().add(all);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(480);
        ButtonType copy = new ButtonType("Копировать", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(copy, ButtonType.CANCEL);
        var copyButton = dialog.getDialogPane().lookupButton(copy);
        choices.forEach(choice -> choice.selectedProperty().addListener((o, a, b) ->
                copyButton.setDisable(choices.stream().noneMatch(CheckBox::isSelected))));
        dialog.showAndWait().filter(copy::equals).ifPresent(result -> {
            List<Path> selected = new ArrayList<>();
            for (int i = 0; i < files.size(); i++) if (choices.get(i).isSelected()) selected.add(files.get(i));
            feedback.accept("Подготовка буфера обмена…");
            CompletableFuture.runAsync(() -> {
                try {
                    ClipboardContent data = content(selected);
                    Platform.runLater(() -> {
                        if (!Clipboard.getSystemClipboard().setContent(data)) feedback.accept("Не удалось записать в буфер обмена");
                        else feedback.accept(data.hasImage() ? "Изображение скопировано — вставьте его в чат" : "Скопировано файлов: " + selected.size());
                    });
                } catch (Exception e) { Platform.runLater(() -> feedback.accept("Ошибка копирования: " + e.getMessage())); }
            });
        });
    }

    static ClipboardContent content(List<Path> files) throws IOException {
        if (files.isEmpty()) throw new IOException("Выберите хотя бы один файл");
        for (Path file : files) if (!Files.isRegularFile(file)) throw new IOException("Файл недоступен: " + file.getFileName());
        ClipboardContent content = new ClipboardContent();
        if (files.size() == 1) {
            Path file = files.get(0);
            try (var input = ImageIO.createImageInputStream(file.toFile())) {
                var readers = ImageIO.getImageReaders(input);
                if (readers.hasNext()) {
                    var reader = readers.next();
                    WritableImage image;
                    try {
                        reader.setInput(input);
                        if ((long) reader.getWidth(0) * reader.getHeight(0) > 40_000_000) {
                            throw new IOException("Изображение слишком большое для буфера (более 40 Мп)");
                        }
                        var decoded = reader.read(0);
                        int width = decoded.getWidth(), height = decoded.getHeight();
                        image = new WritableImage(width, height);
                        int[] row = new int[width];
                        for (int y = 0; y < height; y++) {
                            decoded.getRGB(0, y, width, 1, row, 0, width);
                            image.getPixelWriter().setPixels(0, y, width, 1, PixelFormat.getIntArgbInstance(), row, 0, width);
                        }
                    } finally { reader.dispose(); }
                    content.putImage(image);
                    return content;
                }
            }
        }
        content.putFiles(files.stream().map(Path::toFile).toList());
        return content;
    }
}
