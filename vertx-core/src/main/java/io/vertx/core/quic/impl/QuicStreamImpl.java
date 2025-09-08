/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.quic.impl;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.codec.quic.QuicStreamFrame;
import io.vertx.core.impl.buffer.VertxByteBufAllocator;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.quic.QuicStreamInternal;
import io.vertx.core.net.impl.SocketBase;
import io.vertx.core.quic.QuicConnection;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class QuicStreamImpl extends SocketBase<QuicStreamImpl> implements QuicStreamInternal {

  private final QuicConnection connection;
  private final ContextInternal context;
  private final QuicStreamChannel channel;

  QuicStreamImpl(QuicConnection connection, ContextInternal context, QuicStreamChannel channel, ChannelHandlerContext chctx) {
    super(context, chctx);
    this.connection = connection;
    this.context = context;
    this.channel = channel;
  }
  @Override
  protected void handleMessage(Object msg) {
    if (msg instanceof QuicStreamFrame) {
      QuicStreamFrame frame = (QuicStreamFrame) msg;
      boolean fin = frame.hasFin();
      ByteBuf safe;
      try {
        ByteBuf byteBuf = frame.content();
        safe = VertxByteBufAllocator.DEFAULT.heapBuffer(byteBuf.readableBytes());
        safe.writeBytes(byteBuf, byteBuf.readerIndex(), byteBuf.readableBytes());
      } finally {
        frame.release();
      }
      super.handleMessage(safe);
      if (fin) {
        handleEnd();
      }
    }
  }

  @Override
  protected void writeClose(Object reason, ChannelPromise promise) {
    writeToChannel(QuicStreamFrame.EMPTY_FIN, promise);
  }

  @Override
  public QuicConnection connection() {
    return connection;
  }
}
