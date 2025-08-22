package kardash.project.app.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;

public class GRPCServer implements Runnable {

    private final int port;
    private Server server;

    public GRPCServer(int port) {
        this.port = port;
    }

    @Override
    public void run() {
        try {
            server = ServerBuilder
                    .forPort(port)
                    .addService(new FileReceiver())
                    .addService(new PairingImpl())
                    .addService(new CancelImpl())
                    .build()
                    .start();
            System.out.println("gRPC server started on port " + port);
            server.awaitTermination(); // блокирует до shutdown()
        } catch (IOException e) {
            throw new RuntimeException("Failed to start gRPC server", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // восстановить флаг
            System.out.println("gRPC server thread interrupted");
        } finally {
            if (server != null) {
                server.shutdownNow(); // немедленно остановить
                System.out.println("gRPC server stopped");
            }
        }
    }

    public void stop() {
        if (server != null) {
            server.shutdownNow(); // инициирует остановку
        }
        System.out.println("gRPC server stopped");
    }
}