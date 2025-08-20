package kardash.project.app.grpc;

import io.grpc.stub.StreamObserver;
import kardash.project.proto.transport.FileTransferServiceGrpc;
import kardash.project.proto.transport.TransportFiles;
import java.io.FileOutputStream;
import java.io.IOException;

public class FileReceiver extends FileTransferServiceGrpc.FileTransferServiceImplBase {

    @Override
    public StreamObserver<TransportFiles.FileChunk> sendFile(StreamObserver<TransportFiles.TransferStatus> responseObserver) {

        return new StreamObserver<>() {
            private FileOutputStream fileOutputStream;
            private String fileName;

            @Override
            public void onNext(TransportFiles.FileChunk fileChunk) {
                try {
                    if (fileOutputStream == null) {
                        fileName = fileChunk.getFilename();
                        fileOutputStream = new FileOutputStream("received_" + fileName);
                    }
                    fileOutputStream.write(fileChunk.getContent().toByteArray());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onError(Throwable t) {
                System.out.println("Error: " + t.getMessage());
            }

            @Override
            public void onCompleted() {
                try {
                    if (fileOutputStream != null) {
                        fileOutputStream.close();
                    }
                    responseObserver.onNext(TransportFiles.TransferStatus.newBuilder()
                            .setSuccess(true)
                            .setMessage("File received " + fileName)
                            .build());
                    responseObserver.onCompleted();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }


        };

    }


}
