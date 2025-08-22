package kardash.project.app.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import javafx.application.Platform;
import kardash.project.app.core.cotrollers.WaitingForRequestController;
import kardash.project.app.core.cotrollers.view.ViewController;
import kardash.project.app.models.IncomingRequestContext;
import kardash.project.app.models.TransferContext;
import kardash.project.proto.transport.PairingServiceGrpc;
import kardash.project.proto.transport.TransportFiles;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Time;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class PairingImpl extends PairingServiceGrpc.PairingServiceImplBase {

    @Override
    public void pairingRequest (TransportFiles.PairingRequest pairingRequest, StreamObserver<TransportFiles.PairingResponse> pairingResponse) {

        var context = new IncomingRequestContext(pairingRequest.getPCName(),
                pairingRequest.getPCAddress(),
                pairingRequest.getFileName(),
                pairingRequest.getFileSize());

        TransferContext.setIncomingRequestContext(context);

        WaitingForRequestController.incomingRequest();

        try {
            boolean ok = context.answer.get(30, TimeUnit.SECONDS);
            pairingResponse.onNext(TransportFiles.PairingResponse.newBuilder().setResponse(ok).build());
        } catch (TimeoutException e) {
            // время вышло -> отклоняем
            pairingResponse.onNext(TransportFiles.PairingResponse.newBuilder().setResponse(false).build());
        } catch (Exception e) {
            pairingResponse.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        } finally {
            pairingResponse.onCompleted();
        }
    }

}
