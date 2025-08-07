package org.example.cotrollers;

import org.example.constants.Constants;
import org.example.grpc.FilePublisher;
import org.example.grpc.GRPCServer;

import java.io.IOException;

public class FileController {

    private final FilePublisher filePublisher;


    public FileController() {
        filePublisher = new FilePublisher();
    }

    public void sendFile(String serverIP, String filePath) {

        try {
            filePublisher.sendFile(serverIP, Constants.GRPC_PORT, filePath);
        } catch (IOException | InterruptedException e) {
            e.getStackTrace();
        }

    }


}
