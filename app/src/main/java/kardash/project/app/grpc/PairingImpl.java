package kardash.project.app.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import kardash.project.app.core.cotrollers.FXMLControllers.WaitingForRequestController;
import kardash.project.app.models.IncomingRequestContext;
import kardash.project.app.models.TransferContext;
import kardash.project.proto.transport.PairingServiceGrpc;
import kardash.project.proto.transport.TransportFiles;

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
