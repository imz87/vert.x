/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http.impl;

import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.compression.CompressionOptions;
import io.netty.handler.codec.compression.StandardCompressionOptions;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.vertx.core.Handler;
import io.vertx.core.ThreadingModel;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.impl.http2.Http2ServerChannelInitializer;
import io.vertx.core.http.impl.http2.codec.Http2CodecServerChannelInitializer;
import io.vertx.core.http.impl.http2.multiplex.Http2MultiplexServerChannelInitializer;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.tls.SslContextManager;
import io.vertx.core.internal.net.SslChannelProvider;
import io.vertx.core.net.impl.*;
import io.vertx.core.spi.metrics.HttpServerMetrics;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;

/**
 * A channel initializer that takes care of configuring a blank channel for HTTP to Vert.x {@link io.vertx.core.http.HttpServerRequest}.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class HttpServerConnectionInitializer {

  private final ContextInternal context;
  private final ThreadingModel threadingModel;
  private final Supplier<ContextInternal> streamContextSupplier;
  private final HttpServerImpl server;
  private final HttpServerOptions options;
  private final String serverOrigin;
  private final boolean disableH2C;
  private final Handler<HttpServerConnection> connectionHandler;
  private final Handler<Throwable> exceptionHandler;
  private final Object metric;
  private final CompressionManager compressionManager;
  private final int compressionContentSizeThreshold;
  private final Http2ServerChannelInitializer http2ChannelInitializer;

  HttpServerConnectionInitializer(ContextInternal context,
                                  ThreadingModel threadingModel,
                                  Supplier<ContextInternal> streamContextSupplier,
                                  HttpServerImpl server,
                                  HttpServerOptions options,
                                  String serverOrigin,
                                  Handler<HttpServerConnection> connectionHandler,
                                  Handler<Throwable> exceptionHandler,
                                  Object metric) {

    CompressionManager compressionManager;
    if (options.isCompressionSupported()) {
      CompressionOptions[] compressionOptions;
      List<CompressionOptions> compressors = options.getCompressors();
      if (compressors == null) {
        int compressionLevel = options.getCompressionLevel();
        compressionOptions = new CompressionOptions[] { StandardCompressionOptions.gzip(compressionLevel, 15, 8), StandardCompressionOptions.deflate(compressionLevel, 15, 8) };
      } else {
        compressionOptions = compressors.toArray(new CompressionOptions[0]);
      }
      compressionManager = new CompressionManager(options.getCompressionContentSizeThreshold(), compressionOptions);
    } else {
      compressionManager = null;
    }

    Http2ServerChannelInitializer http2ChannelInitalizer;
    if (options.getHttp2MultiplexImplementation()) {
      http2ChannelInitalizer = new Http2MultiplexServerChannelInitializer(
        context,
        compressionManager,
        options.isDecompressionSupported(),
        (HttpServerMetrics) server.getMetrics(),
        metric,
        streamContextSupplier,
        connectionHandler,
        HttpUtils.fromVertxInitialSettings(true, options.getInitialSettings()),
        options.getLogActivity());
    } else {
      http2ChannelInitalizer = new Http2CodecServerChannelInitializer(
        this,
        (HttpServerMetrics) server.getMetrics(),
        options,
        compressionManager,
        streamContextSupplier,
        connectionHandler,
        serverOrigin,
        metric,
        options.getLogActivity()
      );
    }

    this.context = context;
    this.threadingModel = threadingModel;
    this.streamContextSupplier = streamContextSupplier;
    this.server = server;
    this.options = options;
    this.serverOrigin = serverOrigin;
    this.disableH2C = !options.isHttp2ClearTextEnabled();
    this.connectionHandler = connectionHandler;
    this.exceptionHandler = exceptionHandler;
    this.metric = metric;
    this.compressionManager = compressionManager;
    this.compressionContentSizeThreshold = options.getCompressionContentSizeThreshold();
    this.http2ChannelInitializer = http2ChannelInitalizer;
  }

  void configurePipeline(Channel ch, SslChannelProvider sslChannelProvider, SslContextManager sslContextManager) {
    ChannelPipeline pipeline = ch.pipeline();
    if (options.isSsl()) {
      SslHandler sslHandler = pipeline.get(SslHandler.class);
      if (options.isUseAlpn()) {
        String protocol = sslHandler.applicationProtocol();
        if (protocol != null) {
          switch (protocol) {
            case "h2":
              configureHttp2(ch.pipeline(), true);
              break;
            case "http/1.1":
            case "http/1.0":
              configureHttp1Pipeline(ch.pipeline(), sslChannelProvider, sslContextManager);
              configureHttp1Handler(ch.pipeline(), sslContextManager);
              break;
          }
        } else {
          // No alpn presented or OpenSSL
          configureHttp1Pipeline(ch.pipeline(), sslChannelProvider, sslContextManager);
          configureHttp1Handler(ch.pipeline(), sslContextManager);
        }
      } else {
        configureHttp1Pipeline(ch.pipeline(), sslChannelProvider, sslContextManager);
        configureHttp1Handler(ch.pipeline(), sslContextManager);
      }
    } else {
      if (disableH2C) {
        configureHttp1Pipeline(ch.pipeline(), sslChannelProvider, sslContextManager);
        configureHttp1Handler(ch.pipeline(), sslContextManager);
      } else {
        Http1xOrH2CHandler handler = new Http1xOrH2CHandler() {
          @Override
          protected void configure(ChannelHandlerContext ctx, boolean h2c) {
            if (h2c) {
              configureHttp2(ctx.pipeline(), false);
            } else {
              configureHttp1Pipeline(ctx.pipeline(), sslChannelProvider, sslContextManager);
              http2ChannelInitializer.configureHttp1OrH2CUpgradeHandler(context, ctx.pipeline(), sslChannelProvider, sslContextManager);
            }
          }
          @Override
          public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            super.exceptionCaught(ctx, cause);
            handleException(cause);
          }
        };
        // Handler that detects whether the HTTP/2 connection preface or just process the request
        // with the HTTP 1.x pipeline to support H2C with prior knowledge, i.e a client that connects
        // and uses HTTP/2 in clear text directly without an HTTP upgrade.
        String name = computeChannelName(pipeline);
        pipeline.addBefore(name, null, handler);
      }
    }
  }

  private void handleException(Throwable cause) {
    context.emit(cause, exceptionHandler);
  }

  private void sendServiceUnavailable(Channel ch) {
    ch.writeAndFlush(
      Unpooled.copiedBuffer("HTTP/1.1 503 Service Unavailable\r\n" +
        "Content-Length:0\r\n" +
        "\r\n", StandardCharsets.ISO_8859_1))
      .addListener(ChannelFutureListener.CLOSE);
  }

  private void configureHttp2(ChannelPipeline pipeline, boolean ssl) {
    http2ChannelInitializer.configureHttp2(context, pipeline, ssl);
    checkAccept(pipeline);
  }

  public void checkAccept(ChannelPipeline pipeline) {
    if (!server.requestAccept()) {
      // That should send an HTTP/2 go away
      pipeline.channel().close();
      return;
    }
  }

  /**
   * Compute the name of the logical end of pipeline when adding handlers to a preconfigured NetSocket pipeline.
   * See {@link io.vertx.core.net.impl.NetServerImpl}
   * @param pipeline the channel pipeline
   * @return the name of the handler to use
   */
  private static String computeChannelName(ChannelPipeline pipeline) {
    if (pipeline.get(ChunkedWriteHandler.class) != null) {
      return "chunkedWriter";
    } else if (pipeline.get(IdleStateHandler.class) != null) {
      return "idle";
    } else {
      return "handler";
    }
  }

  private void configureHttp1Pipeline(ChannelPipeline pipeline, SslChannelProvider sslChannelProvider, SslContextManager sslContextManager) {
    String name = computeChannelName(pipeline);
    pipeline.addBefore(name, "httpDecoder", new VertxHttpRequestDecoder(options));
    pipeline.addBefore(name, "httpEncoder", new VertxHttpResponseEncoder());
    if (options.isDecompressionSupported()) {
      pipeline.addBefore(name, "inflater", new HttpContentDecompressor(false));
    }
    if (options.isCompressionSupported()) {
      pipeline.addBefore(name, "deflater", new HttpChunkContentCompressor(compressionContentSizeThreshold, compressionManager.options()));
    }
  }

  public void configureHttp1Handler(ChannelPipeline pipeline, SslContextManager sslContextManager) {
    if (!server.requestAccept()) {
      sendServiceUnavailable(pipeline.channel());
      return;
    }
    HttpServerMetrics metrics = (HttpServerMetrics) server.getMetrics();
    VertxHandler<Http1xServerConnection> handler = VertxHandler.create(chctx -> {
      Http1xServerConnection conn = new Http1xServerConnection(
        threadingModel,
        streamContextSupplier,
        sslContextManager,
        options,
        chctx,
        context,
        serverOrigin,
        metrics);
      conn.metric(metric);
      return conn;
    });
    pipeline.replace(VertxHandler.class, "handler", handler);
    Http1xServerConnection conn = handler.getConnection();
    connectionHandler.handle(conn);
  }
}
