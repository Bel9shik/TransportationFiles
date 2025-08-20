package org.example.grpc;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.example.transport.FileTransferServiceGrpc;
import org.example.transport.TransportFiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class FilePublisher {
    public void sendFile(String serverIP, int serverPort, String filePath) throws IOException, InterruptedException {
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(serverIP, serverPort)
                .usePlaintext()
                .build();

        FileTransferServiceGrpc.FileTransferServiceStub stub = FileTransferServiceGrpc.newStub(channel);

        // Создаем latch для ожидания завершения
        CountDownLatch finishLatch = new CountDownLatch(1);

        Path path = Paths.get(filePath);
        byte[] fileBytes = Files.readAllBytes(path);
        String fileName = path.getFileName().toString();

        StreamObserver<TransportFiles.FileChunk> requestObserver = stub.sendFile(new StreamObserver<>() {
            @Override
            public void onNext(TransportFiles.TransferStatus transferStatus) {
                System.out.println("Status: " + transferStatus.getMessage());
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("Error: " + t.getMessage());
                finishLatch.countDown();
            }

            @Override
            public void onCompleted() {
                System.out.println("File '" + fileName + "' sent successfully!");
                finishLatch.countDown();
            }
        });

        int chunkSize = 1024;
        for (int i = 0; i < fileBytes.length; i += chunkSize) {
            int end = Math.min(i + chunkSize, fileBytes.length);
            TransportFiles.FileChunk chunk = TransportFiles.FileChunk.newBuilder()
                    .setFilename(fileName)
                    .setContent(ByteString.copyFrom(fileBytes, i, end - i))
                    .build();
            requestObserver.onNext(chunk);
        }

        requestObserver.onCompleted();

        // Ожидаем завершения (таймаут 10 минут)
        if (!finishLatch.await(10, TimeUnit.MINUTES)) {
            System.out.println("File sending did not finish within 10 minutes");
        }

        channel.shutdown();
    }
}