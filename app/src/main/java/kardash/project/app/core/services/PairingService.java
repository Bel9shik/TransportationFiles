package kardash.project.app.core.services;

import javafx.concurrent.Service;
import javafx.concurrent.Task;
import kardash.project.app.core.cotrollers.PairingController;

import java.net.InetAddress;

public class PairingService extends Service<Boolean> {

    private final String pcName;
    private final InetAddress ip;

    public PairingService(String pcName, InetAddress ip) {
        this.pcName = pcName;
        this.ip = ip;
    }

    @Override
    protected Task<Boolean> createTask() {
        return new Task<Boolean>() {
            @Override
            protected Boolean call() {
                return new PairingController().pairingRequest(pcName, ip);
            }
        };
    }
}
