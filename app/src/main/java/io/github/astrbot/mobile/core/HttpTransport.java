package io.github.astrbot.mobile.core;

import java.io.*;
import java.net.*;
import java.util.*;

/** Per-connection cookies. Never follows redirects with credentials. */
public final class HttpTransport implements StreamTransport {
  public static final int MAX_BYTES = 16 * 1024 * 1024;
  private final CookieManager cookies;

  public HttpTransport() {
    this(new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER));
  }

  private HttpTransport(CookieManager cookies) {
    this.cookies = cookies;
  }

  public HttpTransport fork() {
    return new HttpTransport(cookies);
  }

  private volatile HttpURLConnection active;

  public void cancel() {
    HttpURLConnection c = active;
    if (c != null) c.disconnect();
  }

  @Override
  public Reply send(String url, String method, Map<String, String> headers, byte[] body)
      throws IOException {
    URI uri = URI.create(url);
    HttpURLConnection c = (HttpURLConnection) uri.toURL().openConnection();
    active = c;
    c.setInstanceFollowRedirects(false);
    c.setConnectTimeout(15000);
    c.setReadTimeout(45000);
    c.setRequestMethod(method);
    for (Map.Entry<String, String> h : headers.entrySet())
      c.setRequestProperty(h.getKey(), h.getValue());
    for (Map.Entry<String, List<String>> h : cookies.get(uri, Collections.emptyMap()).entrySet())
      c.setRequestProperty(h.getKey(), String.join("; ", h.getValue()));
    try {
      if (body != null) {
        c.setDoOutput(true);
        c.setFixedLengthStreamingMode(body.length);
        try (OutputStream o = c.getOutputStream()) {
          o.write(body);
        }
      }
      int status = c.getResponseCode();
      cookies.put(uri, c.getHeaderFields());
      InputStream stream = status >= 400 ? c.getErrorStream() : c.getInputStream();
      byte[] data = stream == null ? new byte[0] : readLimited(stream, MAX_BYTES);
      return new Reply(
          status,
          data,
          c.getContentType() == null ? "application/octet-stream" : c.getContentType());
    } finally {
      c.disconnect();
      active = null;
    }
  }

  @Override
  public Stream openStream(String url, Map<String, String> headers) throws IOException {
    URI uri = URI.create(url);
    HttpURLConnection c = (HttpURLConnection) uri.toURL().openConnection();
    active = c;
    c.setInstanceFollowRedirects(false);
    c.setConnectTimeout(15000);
    c.setReadTimeout(45000);
    for (Map.Entry<String, String> h : headers.entrySet())
      c.setRequestProperty(h.getKey(), h.getValue());
    for (Map.Entry<String, List<String>> h : cookies.get(uri, Collections.emptyMap()).entrySet())
      c.setRequestProperty(h.getKey(), String.join("; ", h.getValue()));
    try {
      int status = c.getResponseCode();
      cookies.put(uri, c.getHeaderFields());
      InputStream body = status >= 400 ? c.getErrorStream() : c.getInputStream();
      return new Stream(
          status,
          c.getContentType() == null ? "" : c.getContentType(),
          body,
          () -> {
            c.disconnect();
            if (active == c) active = null;
          });
    } catch (IOException e) {
      c.disconnect();
      throw e;
    }
  }

  public static byte[] readLimited(InputStream in, int max) throws IOException {
    try (InputStream input = in;
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[8192];
      int n;
      while ((n = input.read(buffer)) != -1) {
        if (out.size() + n > max) throw new IOException("内容超过大小限制，请使用服务器 WebUI 下载大文件");
        out.write(buffer, 0, n);
      }
      return out.toByteArray();
    }
  }
}
