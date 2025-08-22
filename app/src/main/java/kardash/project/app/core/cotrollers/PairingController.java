package kardash.project.app.core.cotrollers;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import kardash.project.app.models.constants.Constants;
import kardash.project.proto.transport.PairingServiceGrpc;
import kardash.project.proto.transport.TransportFiles;

public class PairingController {

    public boolean pairingRequest(String PCName, String hostName, String fileName, String stringSize) {

        TransportFiles.PairingRequest request = TransportFiles.PairingRequest.newBuilder()
                .setPCAddress(hostName)
                .setPCName(PCName)
                .setFileSize(stringSize)
                .setFileName(fileName)
                .build();

        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(hostName, Constants.GRPC_PORT)
                .usePlaintext()
                .build();

        PairingServiceGrpc.PairingServiceBlockingStub blockingStub = PairingServiceGrpc.newBlockingStub(channel);

        TransportFiles.PairingResponse response = blockingStub.pairingRequest(request);
        channel.shutdown();
        return response.getResponse();

    }

}
