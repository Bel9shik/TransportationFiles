package kardash.project.app.grpc;

import io.grpc.stub.StreamObserver;
import javafx.application.Platform;
import kardash.project.app.core.cotrollers.view.ViewController;
import kardash.project.proto.transport.CancelServiceGrpc;
import kardash.project.proto.transport.TransportFiles;

import java.io.IOException;

public class CancelImpl extends CancelServiceGrpc.CancelServiceImplBase {

    @Override
    public void cancel(TransportFiles.CancelRequest req,
                       StreamObserver<TransportFiles.CancelResponse> resp) {
        Platform.runLater(() -> {
            ViewController.showAlert("Пользователь отклонил запрос на сопряжение");
            try {
                ViewController.switchScene("waiting_for_request.fxml");
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        resp.onNext(TransportFiles.CancelResponse.getDefaultInstance());
        resp.onCompleted();
    }
}
