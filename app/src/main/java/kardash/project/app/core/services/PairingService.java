package kardash.project.app.core.services;

import javafx.concurrent.Service;
import javafx.concurrent.Task;
import kardash.project.app.core.cotrollers.PairingController;
import kardash.project.app.models.User;

public class PairingService extends Service<Boolean> {

    private final User user;
    private final String fileName;
    private final String stringSize;

    public PairingService(User user, String name, String size) {
        this.user = user;
        this.fileName = name;
        this.stringSize = size;
    }

    @Override
    protected Task<Boolean> createTask() {
        return new Task<Boolean>() {
            @Override
            protected Boolean call() {
                return new PairingController().pairingRequest(user, fileName, stringSize);
            }
        };
    }
}
