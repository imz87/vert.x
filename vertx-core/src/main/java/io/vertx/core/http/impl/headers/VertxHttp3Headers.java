package io.vertx.core.http.impl.headers;

import io.netty.handler.codec.http.HttpHeaders;
import io.netty.incubator.codec.http3.DefaultHttp3Headers;
import io.netty.incubator.codec.http3.Http3Headers;
import io.vertx.core.MultiMap;

import java.util.Map;
import java.util.Objects;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public class VertxHttp3Headers extends VertxHttpHeadersBase<Http3Headers> implements VertxHttpHeaders {

  public VertxHttp3Headers() {
    this(new DefaultHttp3Headers());
  }

  public VertxHttp3Headers(Http3Headers headers) {
    super(headers);
  }

  @Override
  public void method(String value) {
    this.headers.method(value);
  }

  @Override
  public void authority(String authority) {
    this.headers.authority(authority);
  }

  @Override
  public String authority() {
    return String.valueOf(this.headers.authority());
  }

  @Override
  public void path(String value) {
    this.headers.path(value);
  }

  @Override
  public void scheme(String value) {
    this.headers.scheme(value);
  }

  @Override
  public String path() {
    return String.valueOf(this.headers.path());
  }

  @Override
  public String method() {
    return String.valueOf(this.headers.method());
  }

  @Override
  public String status() {
    return String.valueOf(this.headers.status());
  }

  @Override
  public void status(CharSequence status) {
    this.headers.status(status);
  }

  @Override
  public CharSequence scheme() {
    return this.headers.scheme();
  }

  @Override
  public MultiMap toHeaderAdapter() {
    return new Http3HeadersAdaptor(headers);
  }

  @Override
  public HttpHeaders toHttpHeaders() {
    HeadersMultiMap headers = HeadersMultiMap.httpHeaders();
    for (Map.Entry<CharSequence, CharSequence> header : this.headers) {
      Http3Headers.PseudoHeaderName headerKey = Http3Headers.PseudoHeaderName.getPseudoHeader(header.getKey());
      CharSequence name = headerKey != null ? headerKey.name() : header.getKey();
      headers.add(name, header.getValue());
    }
    return headers;
  }
}