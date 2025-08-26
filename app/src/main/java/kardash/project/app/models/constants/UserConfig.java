package kardash.project.app.models.constants;

import lombok.Getter;

public final class UserConfig {

    @Getter
    private static UserConfig instance;

    private final String DOWNLOAD_FOLDER;

    private UserConfig(String DOWNLOAD_FOLDER) {
        this.DOWNLOAD_FOLDER = DOWNLOAD_FOLDER;
    }

    public static void initialize (String DOWNLOAD_FOLDER) {
        if (instance == null) {
            instance =  new UserConfig(DOWNLOAD_FOLDER);
        }
    }

}
