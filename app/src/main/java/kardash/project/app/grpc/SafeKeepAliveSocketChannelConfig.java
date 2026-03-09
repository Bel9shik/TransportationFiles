package kardash.project.app.grpc;

import io.grpc.netty.shaded.io.netty.channel.ChannelOption;
import io.grpc.netty.shaded.io.netty.channel.socket.SocketChannelConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * Обёртка над SocketChannelConfig, игнорирующая установку SO_KEEPALIVE
 * (на macOS/Java 25 иначе — SocketException: Invalid argument).
 */
final class SafeKeepAliveSocketChannelConfig implements SocketChannelConfig {

    private final SocketChannelConfig delegate;

    SafeKeepAliveSocketChannelConfig(SocketChannelConfig delegate) {
        this.delegate = delegate;
    }

    @Override
    public <T> boolean setOption(ChannelOption<T> option, T value) {
        if (ChannelOption.SO_KEEPALIVE.equals(option)) {
            return true;
        }
        return delegate.setOption(option, value);
    }

    @Override
    public Map<ChannelOption<?>, Object> getOptions() {
        return delegate.getOptions();
    }

    @Override
    public <T> T getOption(ChannelOption<T> option) {
        return delegate.getOption(option);
    }

    @Override
    public boolean setOptions(Map<ChannelOption<?>, ?> options) {
        Map<ChannelOption<?>, ?> filtered = new HashMap<>(options);
        filtered.remove(ChannelOption.SO_KEEPALIVE);
        return delegate.setOptions(filtered);
    }

    @Override
    public int getReceiveBufferSize() {
        return delegate.getReceiveBufferSize();
    }

    @Override
    public SocketChannelConfig setReceiveBufferSize(int receiveBufferSize) {
        delegate.setReceiveBufferSize(receiveBufferSize);
        return this;
    }

    @Override
    public int getSendBufferSize() {
        return delegate.getSendBufferSize();
    }

    @Override
    public SocketChannelConfig setSendBufferSize(int sendBufferSize) {
        delegate.setSendBufferSize(sendBufferSize);
        return this;
    }

    @Override
    public int getSoLinger() {
        return delegate.getSoLinger();
    }

    @Override
    public SocketChannelConfig setSoLinger(int soLinger) {
        delegate.setSoLinger(soLinger);
        return this;
    }

    @Override
    public int getTrafficClass() {
        return delegate.getTrafficClass();
    }

    @Override
    public SocketChannelConfig setTrafficClass(int trafficClass) {
        delegate.setTrafficClass(trafficClass);
        return this;
    }

    @Override
    public boolean isKeepAlive() {
        return delegate.isKeepAlive();
    }

    @Override
    public SocketChannelConfig setKeepAlive(boolean keepAlive) {
        return this;
    }

    @Override
    public boolean isReuseAddress() {
        return delegate.isReuseAddress();
    }

    @Override
    public SocketChannelConfig setReuseAddress(boolean reuseAddress) {
        delegate.setReuseAddress(reuseAddress);
        return this;
    }

    @Override
    public boolean isTcpNoDelay() {
        return delegate.isTcpNoDelay();
    }

    @Override
    public SocketChannelConfig setTcpNoDelay(boolean tcpNoDelay) {
        delegate.setTcpNoDelay(tcpNoDelay);
        return this;
    }

    @Override
    public SocketChannelConfig setPerformancePreferences(int connectionTime, int latency, int bandwidth) {
        delegate.setPerformancePreferences(connectionTime, latency, bandwidth);
        return this;
    }

    @Override
    public boolean isAllowHalfClosure() {
        return delegate.isAllowHalfClosure();
    }

    @Override
    public SocketChannelConfig setAllowHalfClosure(boolean allowHalfClosure) {
        delegate.setAllowHalfClosure(allowHalfClosure);
        return this;
    }

    @Override
    public SocketChannelConfig setConnectTimeoutMillis(int connectTimeoutMillis) {
        delegate.setConnectTimeoutMillis(connectTimeoutMillis);
        return this;
    }

    @Override
    @Deprecated
    public SocketChannelConfig setMaxMessagesPerRead(int maxMessagesPerRead) {
        delegate.setMaxMessagesPerRead(maxMessagesPerRead);
        return this;
    }

    @Override
    public SocketChannelConfig setWriteSpinCount(int writeSpinCount) {
        delegate.setWriteSpinCount(writeSpinCount);
        return this;
    }

    @Override
    public SocketChannelConfig setAllocator(io.grpc.netty.shaded.io.netty.buffer.ByteBufAllocator allocator) {
        delegate.setAllocator(allocator);
        return this;
    }

    @Override
    public SocketChannelConfig setRecvByteBufAllocator(io.grpc.netty.shaded.io.netty.channel.RecvByteBufAllocator allocator) {
        delegate.setRecvByteBufAllocator(allocator);
        return this;
    }

    @Override
    public SocketChannelConfig setAutoRead(boolean autoRead) {
        delegate.setAutoRead(autoRead);
        return this;
    }

    @Override
    public boolean isAutoClose() {
        return delegate.isAutoClose();
    }

    @Override
    public SocketChannelConfig setAutoClose(boolean autoClose) {
        delegate.setAutoClose(autoClose);
        return this;
    }

    @Override
    public boolean isAutoRead() {
        return delegate.isAutoRead();
    }

    @Override
    public int getConnectTimeoutMillis() {
        return delegate.getConnectTimeoutMillis();
    }

    @Override
    public int getWriteSpinCount() {
        return delegate.getWriteSpinCount();
    }

    @Override
    public io.grpc.netty.shaded.io.netty.buffer.ByteBufAllocator getAllocator() {
        return delegate.getAllocator();
    }

    @Override
    public io.grpc.netty.shaded.io.netty.channel.RecvByteBufAllocator getRecvByteBufAllocator() {
        return delegate.getRecvByteBufAllocator();
    }

    @Override
    public int getMaxMessagesPerRead() {
        return delegate.getMaxMessagesPerRead();
    }

    @Override
    public int getWriteBufferHighWaterMark() {
        return delegate.getWriteBufferHighWaterMark();
    }

    @Override
    public SocketChannelConfig setWriteBufferHighWaterMark(int writeBufferHighWaterMark) {
        delegate.setWriteBufferHighWaterMark(writeBufferHighWaterMark);
        return this;
    }

    @Override
    public int getWriteBufferLowWaterMark() {
        return delegate.getWriteBufferLowWaterMark();
    }

    @Override
    public SocketChannelConfig setWriteBufferLowWaterMark(int writeBufferLowWaterMark) {
        delegate.setWriteBufferLowWaterMark(writeBufferLowWaterMark);
        return this;
    }

    @Override
    public io.grpc.netty.shaded.io.netty.channel.WriteBufferWaterMark getWriteBufferWaterMark() {
        return delegate.getWriteBufferWaterMark();
    }

    @Override
    public SocketChannelConfig setWriteBufferWaterMark(io.grpc.netty.shaded.io.netty.channel.WriteBufferWaterMark writeBufferWaterMark) {
        delegate.setWriteBufferWaterMark(writeBufferWaterMark);
        return this;
    }

    @Override
    public io.grpc.netty.shaded.io.netty.channel.MessageSizeEstimator getMessageSizeEstimator() {
        return delegate.getMessageSizeEstimator();
    }

    @Override
    public SocketChannelConfig setMessageSizeEstimator(io.grpc.netty.shaded.io.netty.channel.MessageSizeEstimator estimator) {
        delegate.setMessageSizeEstimator(estimator);
        return this;
    }
}
