package kardash.project.app.models.constants;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;
import java.util.prefs.Preferences;
import static org.junit.jupiter.api.Assertions.*;

class UserConfigTest {
    @TempDir Path directory;
    private Preferences preferences;
    private UserConfig config;
    private Path downloads;

    @BeforeEach void setup() {
        preferences = Preferences.userRoot().node("transport-files-tests/" + UUID.randomUUID());
        downloads = directory.resolve("Downloads");
        config = new UserConfig(preferences, downloads);
    }

    @AfterEach void cleanup() throws Exception { preferences.removeNode(); preferences.flush(); }

    @Test void defaultsToDownloadsAndCreatesItOnAcceptance() throws Exception {
        assertEquals(downloads, config.downloadDirectory());
        assertFalse(Files.exists(downloads));
        assertEquals(downloads.resolve("photo.png"), config.targetFor("photo.png"));
        assertTrue(Files.isDirectory(downloads));
    }

    @Test void remembersChosenFolderAndCanResetToDownloads() throws Exception {
        Path selected = Files.createDirectory(directory.resolve("Мои файлы"));
        config.setDownloadDirectory(selected);
        UserConfig reopened = new UserConfig(preferences, downloads);
        assertEquals(selected.resolve("file.txt"), reopened.targetFor("file.txt"));
        reopened.resetDownloadDirectory();
        assertEquals(downloads, new UserConfig(preferences, downloads).downloadDirectory());
    }

    @Test void addsNumberWithoutOverwritingExistingFileOrDirectory() throws Exception {
        Files.createDirectories(downloads);
        Path original = Files.writeString(downloads.resolve("photo.png"), "keep me");
        Files.createDirectory(downloads.resolve("photo (1).png"));
        assertEquals(downloads.resolve("photo (2).png"), config.targetFor("photo.png"));
        assertEquals("keep me", Files.readString(original));
        Files.writeString(downloads.resolve("README"), "text");
        assertEquals(downloads.resolve("README (1)"), config.targetFor("README"));
    }

    @Test void unavailableCustomFolderDoesNotSilentlyChangeDestination() throws Exception {
        Path selected = Files.createDirectory(directory.resolve("selected"));
        config.setDownloadDirectory(selected);
        Files.delete(selected);
        assertThrows(IOException.class, () -> config.targetFor("file"));
        assertEquals(selected, config.downloadDirectory());
        assertFalse(Files.exists(selected));
        assertThrows(IOException.class, () -> config.setDownloadDirectory(directory.resolve("missing")));
    }

    @Test void rejectsUnsafeIncomingNameBeforeCreatingDirectories() {
        assertThrows(IllegalArgumentException.class, () -> config.targetFor("../outside"));
        assertFalse(Files.exists(downloads));
    }
}
