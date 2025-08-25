package kardash.project.app.models;

import lombok.Getter;
import lombok.Setter;

public final class TransferContext {

    @Getter
    @Setter
    private static User anotherUser;

    @Getter
    @Setter
    private static User meUser;

    @Getter
    @Setter
    private static SendingFile file;

    @Getter
    @Setter
    private static IncomingRequestContext incomingRequestContext;

    public static void clearAnotherUser() {
        anotherUser = null;
    }

    public static void clearMeUser() {
        meUser = null;
    }


    public static void clearFile() {
        file = null;
    }

    public static void clearIncomingRequestContext() {
        incomingRequestContext = null;
    }

}
