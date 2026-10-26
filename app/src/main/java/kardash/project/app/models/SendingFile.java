package kardash.project.app.models;

import java.io.File;
import java.util.List;

public record SendingFile(List<File> files, String stringSize) {
    public SendingFile { files = List.copyOf(files); }
    public String description() { return files.size() == 1 ? files.get(0).getName() : "Файлов: " + files.size(); }
}
