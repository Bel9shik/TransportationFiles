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
    private static SendingFile file;

    @Getter
    @Setter
    private static IncomingRequestContext incomingRequestContext;

    public static void clearUser() {
        user = null;
    }

    public static void clearFile() {
        file = null;
    }

    public static void clearIncomingRequestContext() {
        incomingRequestContext = null;
    }

}
