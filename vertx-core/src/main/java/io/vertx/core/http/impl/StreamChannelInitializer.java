package io.vertx.core.http.impl;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.vertx.core.Handler;

import java.util.function.Supplier;

class StreamChannelInitializer extends ChannelInitializer<QuicStreamChannel> {
  private static final InternalLogger logger = InternalLoggerFactory.getInstance(StreamChannelInitializer.class);
  private final Supplier<ChannelHandler> channelHandlerSupplier;
  private final Supplier<? extends Http3ConnectionBase> connectionSupplier;
  private final String agentType;
  private final Handler<QuicStreamChannel> onComplete;

  public StreamChannelInitializer(Supplier<ChannelHandler> channelHandlerSupplier, String agentType, Supplier<? extends Http3ConnectionBase> connectionSupplier) {
    this(channelHandlerSupplier, agentType, connectionSupplier, null);
  }

  public StreamChannelInitializer(Supplier<ChannelHandler> channelHandlerSupplier, String agentType, Supplier<?
    extends Http3ConnectionBase> connectionSupplier,
                                  Handler<QuicStreamChannel> onComplete) {
    this.channelHandlerSupplier = channelHandlerSupplier;
    this.agentType = agentType;
    this.onComplete = onComplete;
    this.connectionSupplier = connectionSupplier;
  }

  @Override
  protected void initChannel(QuicStreamChannel streamChannel) {
    logger.debug("{} - Initialize streamChannel with channelId: {}, streamId: ", agentType, streamChannel.id(),
      streamChannel.streamId());

    streamChannel.closeFuture().addListener(future -> {
      VertxHttpStreamBase vertxStream = VertxHttp3ConnectionHandler.getVertxStreamFromStreamChannel(streamChannel);
      if (vertxStream != null) {
        connectionSupplier.get().onStreamClosed(vertxStream);
      }
    });

    streamChannel.pipeline().addLast(channelHandlerSupplier.get());
    if (onComplete != null) {
      onComplete.handle(streamChannel);
    }
  }
}