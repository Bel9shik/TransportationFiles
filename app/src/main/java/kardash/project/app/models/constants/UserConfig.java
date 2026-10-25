package kardash.project.app.models.constants;

import kardash.project.transfer.TransferNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public final class UserConfig {
    private static final String DOWNLOAD_FOLDER = "downloadDirectory";
    private final Preferences preferences;
    private final Path downloads;

    private static final class Holder {
        static final UserConfig INSTANCE = new UserConfig(
                Preferences.userRoot().node("kardash/project/transportation-files"), systemDownloads());
    }

    public static UserConfig getInstance() { return Holder.INSTANCE; }

    public UserConfig(Preferences preferences, Path downloads) {
        this.preferences = preferences;
        this.downloads = downloads.toAbsolutePath();
    }

    public Path downloadDirectory() {
        return Path.of(preferences.get(DOWNLOAD_FOLDER, downloads.toString()));
    }

    public void setDownloadDirectory(Path directory) throws IOException, BackingStoreException {
        checkDirectory(directory);
        save(directory.toAbsolutePath().toString());
    }

    public void resetDownloadDirectory() throws BackingStoreException { save(null); }

    private void save(String directory) throws BackingStoreException {
        String previous = preferences.get(DOWNLOAD_FOLDER, null);
        if (directory == null) preferences.remove(DOWNLOAD_FOLDER);
        else preferences.put(DOWNLOAD_FOLDER, directory);
        try { preferences.flush(); }
        catch (BackingStoreException e) {
            if (previous == null) preferences.remove(DOWNLOAD_FOLDER);
            else preferences.put(DOWNLOAD_FOLDER, previous);
            throw e;
        }
    }

    public Path targetFor(String filename) throws IOException {
        TransferNode.validateFilename(filename);
        Path directory = downloadDirectory();
        if (preferences.get(DOWNLOAD_FOLDER, null) == null) Files.createDirectories(directory);
        checkDirectory(directory);
        int dot = filename.lastIndexOf('.');
        String stem = dot > 0 ? filename.substring(0, dot) : filename;
        String extension = dot > 0 ? filename.substring(dot) : "";
        for (int i = 0; i < 10_000; i++) {
            Path target = directory.resolve(i == 0 ? filename : stem + " (" + i + ")" + extension);
            if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return target;
        }
        throw new IOException("Слишком много файлов с этим именем. Выберите «Сохранить как…».");
    }

    private static void checkDirectory(Path directory) throws IOException {
        if (!Files.isDirectory(directory) || !Files.isWritable(directory)) {
            throw new IOException("Папка недоступна для записи: " + directory + ". Выберите другую папку.");
        }
    }

    static Path systemDownloads() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String[] command = os.startsWith("windows") ? new String[]{"powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
                "[Console]::OutputEncoding=[Text.Encoding]::UTF8; "
                        + "[Environment]::ExpandEnvironmentVariables((Get-ItemProperty -LiteralPath "
                        + "'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\User Shell Folders')"
                        + ".'{374DE290-123F-4565-9164-39C4925E467B}')"}
                : os.contains("linux") ? new String[]{"xdg-user-dir", "DOWNLOAD"} : null;
        if (command != null) {
            Process process = null;
            try {
                process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
                if (process.waitFor(3, TimeUnit.SECONDS) && process.exitValue() == 0) {
                    String value = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                            .replace("\uFEFF", "").strip();
                    if (!value.isEmpty() && Path.of(value).isAbsolute()) return Path.of(value);
                }
            } catch (IOException | InvalidPathException ignored) { /* Fall back to the standard home directory below. */ }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            finally { if (process != null) process.destroy(); }
        }
        return Path.of(System.getProperty("user.home"), "Downloads");
    }
}
