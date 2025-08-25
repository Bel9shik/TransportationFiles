package kardash.project.app.core.cotrollers;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import kardash.project.app.models.User;
import kardash.project.app.models.constants.Constants;
import kardash.project.proto.transport.PairingServiceGrpc;
import kardash.project.proto.transport.TransportFiles;

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

        PairingServiceGrpc.PairingServiceBlockingStub blockingStub = PairingServiceGrpc.newBlockingStub(channel);

        TransportFiles.PairingResponse response = blockingStub.pairingRequest(request);
        channel.shutdown();
        return response.getResponse();

    }

}
