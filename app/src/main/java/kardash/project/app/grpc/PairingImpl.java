package kardash.project.app.grpc;

import io.grpc.stub.StreamObserver;
import kardash.project.proto.transport.PairingServiceGrpc;
import kardash.project.proto.transport.TransportFiles;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class PairingImpl extends PairingServiceGrpc.PairingServiceImplBase {

    @Override
    public void pairingRequest (TransportFiles.PairingRequest pairingRequest, StreamObserver<TransportFiles.PairingResponse> pairingResponse) {

        System.out.println(pairingRequest.getPCName() + "(" + pairingRequest.getPCAddress() + ")" + " хочет провести сопряжение с вами");
        try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {

            String userResponse = br.readLine();

            pairingResponse.onNext(TransportFiles.PairingResponse
                    .newBuilder()
                    .setResponse(userResponse.equalsIgnoreCase("yes"))
                    .build());
        } catch (IOException e) {
            e.printStackTrace();
        }
        pairingResponse.onCompleted();
    }

}
