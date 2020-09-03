package io.joyrpc.transport.netty4.channel;

/*-
 * #%L
 * joyrpc
 * %%
 * Copyright (C) 2019 joyrpc.io
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import io.joyrpc.event.AsyncResult;
import io.joyrpc.exception.ChannelClosedException;
import io.joyrpc.exception.LafException;
import io.joyrpc.exception.OverloadException;
import io.joyrpc.transport.buffer.ChannelBuffer;
import io.joyrpc.transport.channel.Channel;
import io.joyrpc.transport.channel.FutureManager;
import io.joyrpc.transport.channel.SendResult;
import io.joyrpc.transport.message.Message;
import io.joyrpc.transport.netty4.buffer.NettyChannelBuffer;
import io.joyrpc.transport.session.SessionManager;
import io.netty.channel.ChannelFuture;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;

import java.net.InetSocketAddress;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @date: 2019/1/15
 */
public class NettyChannel implements Channel {
    protected static final String SEND_REQUEST_TOO_FAST = "Send request exception, because sending request is too fast, causing channel is not writable. at %s : %s";
    protected static final String SEND_REQUEST_NOT_ACTIVE = "Send request exception, causing channel is not active. at  %s : %s";
    /**
     * 通道接口
     */
    protected io.netty.channel.Channel channel;
    /**
     * 消息ID，默认采用int
     */
    protected AtomicInteger idGenerator = new AtomicInteger(0);
    /**
     * Future管理器
     */
    protected FutureManager<Long, Message> futureManager;
    /**
     * 会话管理器
     */
    protected SessionManager sessionManager;
    /**
     * 是否是服务端
     */
    protected boolean server;

    /**
     * 构造函数
     *
     * @param channel 通道
     * @param server  服务端标识
     */
    public NettyChannel(io.netty.channel.Channel channel, boolean server) {
        this.channel = channel;
        this.server = server;
        this.futureManager = new FutureManager<>(this, () -> (long) idGenerator.incrementAndGet());
        this.sessionManager = new SessionManager(server);
    }

    @Override
    public void send(final Object object, final Consumer<SendResult> consumer) {
        if (!isWritable()) {
            LafException throwable = isActive() ?
                    new OverloadException(String.format(SEND_REQUEST_TOO_FAST, Channel.toString(this), object.toString()), 0, isServer()) :
                    new ChannelClosedException(String.format(SEND_REQUEST_NOT_ACTIVE, Channel.toString(this), object.toString()));
            if (consumer != null) {
                consumer.accept(new SendResult(throwable, this));
            } else {
                throw throwable;
            }
        } else if (consumer != null) {
            try {
                channel.writeAndFlush(object).addListener((future) -> {
                    if (future.isSuccess()) {
                        consumer.accept(new SendResult(true, this, object));
                    } else {
                        consumer.accept(new SendResult(future.cause(), this, object));
                    }
                });
            } catch (Throwable e) {
                consumer.accept(new SendResult(e, this));
            }
        } else {
            channel.writeAndFlush(object, channel.voidPromise());
        }
    }

    @Override
    public boolean close() {
        return execute(channel::close);
    }

    @Override
    public void close(final Consumer<AsyncResult<Channel>> consumer) {
        execute(channel::close, consumer);
    }

    /**
     * 执行
     *
     * @param callable
     * @return
     */
    protected boolean execute(final Callable<ChannelFuture> callable) {
        ChannelFuture future = null;
        try {
            future = callable.call();
            future.await();
        } catch (InterruptedException e) {
        } catch (Throwable e) {
        }
        return future != null ? future.isSuccess() : false;
    }

    /**
     * 执行
     *
     * @param callable 调用
     * @param consumer 消费者
     */
    protected void execute(final Callable<ChannelFuture> callable, final Consumer<AsyncResult<Channel>> consumer) {
        try {
            ChannelFuture future = callable.call();
            if (consumer != null) {
                future.addListener(f -> {
                    if (f.isSuccess()) {
                        consumer.accept(new AsyncResult<>(this));
                    } else {
                        consumer.accept(new AsyncResult<>(this, f.cause()));
                    }
                });
            }
        } catch (Throwable e) {
            if (consumer != null) {
                consumer.accept(new AsyncResult<>(this, e));
            }
        }
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        return (InetSocketAddress) channel.localAddress();
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return (InetSocketAddress) channel.remoteAddress();
    }

    @Override
    public boolean isWritable() {
        return channel.isWritable();
    }

    @Override
    public boolean isActive() {
        return channel.isActive();
    }

    @Override
    public <T> T getAttribute(final String key) {
        if (key == null) {
            return null;
        }
        Attribute<T> attribute = channel.attr(AttributeKey.valueOf(key));
        return attribute.get();
    }

    @Override
    public <T> T getAttribute(final String key, final Function<String, T> function) {
        if (key == null) {
            return null;
        }
        Attribute<T> attribute = channel.attr(AttributeKey.valueOf(key));
        T result = attribute.get();
        if (result == null && function != null) {
            T target = function.apply(key);
            if (target != null) {
                if (attribute.compareAndSet(null, target)) {
                    return target;
                } else {
                    return attribute.get();
                }
            }
        }
        return result;
    }

    @Override
    public Channel setAttribute(final String key, final Object value) {
        if (key != null) {
            channel.attr(AttributeKey.valueOf(key)).set(value);
        }
        return this;
    }

    @Override
    public Object removeAttribute(final String key) {
        if (key == null) {
            return null;
        }
        return channel.attr(AttributeKey.valueOf(key)).getAndSet(null);
    }

    @Override
    public FutureManager<Long, Message> getFutureManager() {
        return futureManager;
    }

    @Override
    public ChannelBuffer buffer() {
        return new NettyChannelBuffer(channel.alloc().buffer());
    }

    @Override
    public ChannelBuffer buffer(final int initialCapacity) {
        return new NettyChannelBuffer(channel.alloc().buffer(initialCapacity));
    }

    @Override
    public ChannelBuffer buffer(final int initialCapacity, final int maxCapacity) {
        return new NettyChannelBuffer(channel.alloc().buffer(initialCapacity, maxCapacity));
    }

    @Override
    public SessionManager getSessionManager() {
        return sessionManager;
    }

    @Override
    public boolean isServer() {
        return server;
    }

    @Override
    public void fireCaught(Throwable cause) {
        channel.pipeline().fireExceptionCaught(cause);
    }
}
