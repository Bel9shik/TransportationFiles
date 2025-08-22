package kardash.project.app.core.services;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import javafx.concurrent.Task;
import kardash.project.proto.transport.CancelServiceGrpc;
import kardash.project.proto.transport.TransportFiles;

public class CancelService {

    public static void callAsync(String ip, int port) {
        Task<Void> t = new Task<>() {
            @Override protected Void call() {
                ManagedChannel ch = ManagedChannelBuilder
                        .forAddress(ip, port)
                        .usePlaintext()
                        .build();
                CancelServiceGrpc.CancelServiceBlockingStub stub =
                        CancelServiceGrpc.newBlockingStub(ch);
                stub.cancel(TransportFiles.CancelRequest.getDefaultInstance());
                ch.shutdown();
                return null;
            }
        };
        new Thread(t).start();
    }
}
