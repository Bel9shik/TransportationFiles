package kardash.project.app.cotrollers.files;

import kardash.project.app.grpc.FilePublisher;

import java.io.IOException;

public class SendController {
    private final FilePublisher filePublisher;

    public SendController() {
        filePublisher = new FilePublisher();
    }

    public void sendFile(String serverIP, int grpcPort, String filePath) {
        try {
            filePublisher.sendFile(serverIP, grpcPort, filePath);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
