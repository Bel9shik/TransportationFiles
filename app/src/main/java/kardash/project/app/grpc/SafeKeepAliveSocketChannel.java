package kardash.project.app.grpc;

import io.grpc.netty.shaded.io.netty.channel.Channel;
import io.grpc.netty.shaded.io.netty.channel.ChannelOutboundBuffer;
import io.grpc.netty.shaded.io.netty.channel.socket.SocketChannelConfig;
import io.grpc.netty.shaded.io.netty.channel.socket.nio.NioSocketChannel;

import java.lang.reflect.Field;
import java.nio.channels.SocketChannel;

/**
 * NioSocketChannel с конфигом-обёрткой, игнорирующей SO_KEEPALIVE (на macOS/Java 25 — Invalid argument).
 * При doWrite временно подменяем конфиг на оригинал, чтобы внутренний cast в Netty не падал.
 */
final class SafeKeepAliveSocketChannel extends NioSocketChannel {

    private static final Field CONFIG_FIELD = configField();
    private final SocketChannelConfig wrapperConfig;
    private final SocketChannelConfig realConfig;

    SafeKeepAliveSocketChannel(Channel parent, SocketChannel socket) {
        super(parent, socket);
        try {
            realConfig = (SocketChannelConfig) CONFIG_FIELD.get(this);
            wrapperConfig = new SafeKeepAliveSocketChannelConfig(realConfig);
            CONFIG_FIELD.set(this, wrapperConfig);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to wrap socket config", e);
        }
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        try {
            CONFIG_FIELD.set(this, realConfig);
            super.doWrite(in);
        } finally {
            CONFIG_FIELD.set(this, wrapperConfig);
        }
    }

    private static Field configField() {
        try {
            Field f = NioSocketChannel.class.getDeclaredField("config");
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("NioSocketChannel.config field not found", e);
        }
    }
}
