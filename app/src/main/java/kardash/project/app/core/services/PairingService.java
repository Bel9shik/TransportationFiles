package kardash.project.app.core.services;

import javafx.concurrent.Service;
import javafx.concurrent.Task;
import kardash.project.app.core.cotrollers.PairingController;

public class PairingService extends Service<Boolean> {

    private final String pcName;
    private final String ip;
    private final String fileName;
    private final String stringSize;

    public PairingService(String pcName, String ip, String name, String size) {
        this.pcName = pcName;
        this.ip = ip;
        this.fileName = name;
        this.stringSize = size;
    }

    @Override
    protected Task<Boolean> createTask() {
        return new Task<Boolean>() {
            @Override
            protected Boolean call() {
                return new PairingController().pairingRequest(pcName, ip, fileName, stringSize);
            }
        };
    }
}
