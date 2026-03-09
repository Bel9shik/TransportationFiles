package kardash.project.app.core.services;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import javafx.concurrent.Task;
import kardash.project.proto.transport.CancelServiceGrpc;
import kardash.project.proto.transport.TransportFiles;

import java.util.concurrent.TimeUnit;

public class CancelService {

    public static void callAsync(String ip, int port) {
        Task<Void> t = new Task<>() {
            @Override
            protected Void call() {
                ManagedChannel ch = ManagedChannelBuilder
                        .forAddress(ip, port)
                        .usePlaintext()
                        .build();
                try {
                    CancelServiceGrpc.CancelServiceBlockingStub stub =
                            CancelServiceGrpc.newBlockingStub(ch);
                    stub.cancel(TransportFiles.CancelRequest.getDefaultInstance());
                } finally {
                    ch.shutdown();
                    try {
                        ch.awaitTermination(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        ch.shutdownNow();
                    }
                }
                return null;
            }
        };
        new Thread(t).start();
    }
}
