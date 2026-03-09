package kardash.project.app.core.cotrollers.FXMLControllers;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import kardash.project.app.models.User;
import kardash.project.proto.transport.PairingServiceGrpc;
import kardash.project.proto.transport.TransportFiles;

import java.util.concurrent.TimeUnit;

public class PairingController {

    public boolean pairingRequest(User user, String fileName, String stringSize) {
        TransportFiles.PairingRequest request = TransportFiles.PairingRequest.newBuilder()
                .setPCName(user.hostName())
                .setPCAddress(user.ip())
                .setFileSize(stringSize)
                .setFileName(fileName)
                .build();

        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(user.ip(), user.port())
                .usePlaintext()
                .build();

        try {
            PairingServiceGrpc.PairingServiceBlockingStub blockingStub = PairingServiceGrpc.newBlockingStub(channel);
            TransportFiles.PairingResponse response = blockingStub.pairingRequest(request);
            return response.getResponse();
        } finally {
            channel.shutdown();
            try {
                channel.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                channel.shutdownNow();
            }
        }
    }
}
