package kardash.project.app.grpc;

import io.grpc.netty.shaded.io.netty.channel.socket.nio.NioServerSocketChannel;

import java.nio.channels.SocketChannel;
import java.util.List;

/**
 * NioServerSocketChannel, который при accept создаёт SafeKeepAliveSocketChannel вместо NioSocketChannel.
 * Так gRPC не падает при установке SO_KEEPALIVE на принятом соединении (Invalid argument на macOS/Java 25).
 */
public final class SafeKeepAliveServerSocketChannel extends NioServerSocketChannel {

    /** Нужен для ReflectiveChannelFactory в NettyServerBuilder.channelType(Class). */
    public SafeKeepAliveServerSocketChannel() {
    }

    @Override
    protected int doReadMessages(List<Object> buf) throws Exception {
        SocketChannel ch = javaChannel().accept();
        try {
            if (ch != null) {
                buf.add(new SafeKeepAliveSocketChannel(this, ch));
                return 1;
            }
        } catch (Throwable t) {
            io.grpc.netty.shaded.io.netty.util.internal.logging.InternalLoggerFactory
                    .getInstance(SafeKeepAliveServerSocketChannel.class)
                    .warn("Failed to create a new channel from an accepted socket.", t);
            try {
                ch.close();
            } catch (Throwable t2) {
                io.grpc.netty.shaded.io.netty.util.internal.logging.InternalLoggerFactory
                        .getInstance(SafeKeepAliveServerSocketChannel.class)
                        .warn("Failed to close a socket.", t2);
            }
        }
        return 0;
    }
}
