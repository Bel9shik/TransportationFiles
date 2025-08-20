package kardash.project.app.models;

import lombok.Getter;
import lombok.Setter;

import java.io.File;

public final class TransferContext {

    @Getter
    @Setter
    private static User user;

    @Getter
    @Setter
    private static File file;

    public static void clearUser() {
        user = null;
    }

    public static void clearFile() {
        file = null;
    }

}
