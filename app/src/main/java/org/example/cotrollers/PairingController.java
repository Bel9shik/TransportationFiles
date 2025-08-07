package org.example.cotrollers;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.example.constants.Constants;
import org.example.transport.PairingServiceGrpc;
import org.example.transport.TransportFiles;

import java.net.InetAddress;

public class PairingController {

    public boolean pairingRequest(String PCName, InetAddress ipAddress) {

        TransportFiles.PairingRequest request = TransportFiles.PairingRequest.newBuilder()
                .setPCAddress(ipAddress.getCanonicalHostName())
                .setPCName(PCName)
                .build();

        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(ipAddress.getHostName(), Constants.GRPC_PORT)
                .usePlaintext()
                .build();

        PairingServiceGrpc.PairingServiceBlockingStub blockingStub = PairingServiceGrpc.newBlockingStub(channel);

        TransportFiles.PairingResponse response = blockingStub.pairingRequest(request);
        return response.getResponse();

    }

}
