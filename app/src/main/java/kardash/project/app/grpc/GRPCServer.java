package kardash.project.app.grpc;

import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.channel.nio.NioEventLoopGroup;
import kardash.project.app.models.TransferContext;
import kardash.project.app.models.User;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

public class GRPCServer implements Runnable {

    private Server server;
    private NioEventLoopGroup bossGroup;
    private NioEventLoopGroup workerGroup;

    @Override
    public void run() {
        try {
            // При кастомном channelType gRPC требует явно задать boss и worker event loop groups.
            bossGroup = new NioEventLoopGroup(1);
            workerGroup = new NioEventLoopGroup();
            // Кастомный ServerChannel создаёт дочерние каналы, у которых установка SO_KEEPALIVE
            // игнорируется (на macOS/Java 25 иначе — Invalid argument и разрыв соединения).
            server = NettyServerBuilder.forAddress(new InetSocketAddress("0.0.0.0", 0))
                    .channelType(SafeKeepAliveServerSocketChannel.class)
                    .bossEventLoopGroup(bossGroup)
                    .workerEventLoopGroup(workerGroup)
                    .addService(new FileReceiver())
                    .addService(new PairingImpl())
                    .addService(new CancelImpl())
                    .build()
                    .start();
            TransferContext.setMeUser(new User("", server.getPort(), "", System.currentTimeMillis()));
            System.out.println("gRPC server started on port " + server.getPort());
            server.awaitTermination(); // блокирует до shutdown()
        } catch (IOException e) {
            throw new RuntimeException("Failed to start gRPC server", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // восстановить флаг
            System.out.println("gRPC server thread interrupted");
        } finally {
            if (server != null) {
                server.shutdownNow(); // немедленно остановить
            }
            shutdownEventLoopGroups();
            System.out.println("gRPC server stopped");
        }
    }

    public void stop() {
        if (server != null) {
            server.shutdownNow(); // инициирует остановку
        }
        shutdownEventLoopGroups();
        System.out.println("gRPC server stopped");
    }

    private void shutdownEventLoopGroups() {
        if (bossGroup != null) {
            bossGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS);
            bossGroup = null;
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS);
            workerGroup = null;
        }
    }
}