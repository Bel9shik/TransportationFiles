package org.example.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;

public class GRPCServer implements Runnable { //вообще вопрос нужен ли этот класс

    private final int port;

    public GRPCServer(int port) {
        this.port = port;
    }

    @Override
    public void run() {
        Server server;
        try {
            server = ServerBuilder
                    .forPort(port)
                    .addService(new FileReceiver()) // Ваша реализация сервиса
                    .addService(new PairingImpl())
                    .build()
                    .start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("Server started on port " + port);
        try {
            server.awaitTermination();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
