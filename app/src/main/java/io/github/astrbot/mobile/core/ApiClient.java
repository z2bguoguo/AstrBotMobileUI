package io.github.astrbot.mobile.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

/** Fallback only on read-only contract discovery, never replay a mutation. */
public final class ApiClient {
  public final Connection connection;
  private final Transport transport;
  private String token = "";
  public JSONObject spec = new JSONObject();
  public boolean modern;
  public final Set<String> legacyFeatures = java.util.concurrent.ConcurrentHashMap.newKeySet();

  public ApiClient(Connection c, Transport t) {
    connection = c;
    transport = t;
  }

  public static final class ApiException extends IOException {
    public final int status;

    public ApiException(int status, String message) {
      super(message);
      this.status = status;
    }
  }

  public JSONObject discover(JSONObject bundled) throws Exception {
    Transport.Reply reply = request("/api/v1/openapi.json", "GET", null, null);
    if (reply.status == 404 || reply.status == 405) {
      modern = false;
      spec = bundled;
      return spec;
    }
    checkHttp(reply);
    JSONObject doc = new JSONObject(reply.text());
    if (!doc.has("paths")) throw new IOException("服务器未返回 OpenAPI；请检查地址或反向代理");
    modern = true;
    spec = doc;
    return spec;
  }

  public void login(String code) throws Exception {
    if (!connection.apiKey.isBlank()) return;
    String password = connection.password;
    if (connection.passwordMode.equals("md5")) {
      byte[] digest =
          MessageDigest.getInstance("MD5").digest(password.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (byte b : digest) hex.append(String.format(Locale.ROOT, "%02x", b & 255));
      password = hex.toString();
    }
    JSONObject body =
        new JSONObject().put("username", connection.username).put("password", password);
    if (!code.isBlank()) body.put("code", code);
    JSONObject result =
        json(
            request(
                modern ? "/api/v1/auth/login" : "/api/auth/login",
                "POST",
                body.toString().getBytes(StandardCharsets.UTF_8),
                "application/json"));
    JSONObject data = result.optJSONObject("data");
    token = data == null ? "" : data.optString("token");
    if (token.isBlank()) throw new ApiException(401, "登录未完成：可能需要两步验证码或密码升级，请查看服务器返回及 WebUI");
  }

  ApiClient forkReads() {
    ApiClient child =
        new ApiClient(
            connection,
            transport instanceof HttpTransport ? ((HttpTransport) transport).fork() : transport);
    child.token = token;
    child.modern = modern;
    child.spec = spec;
    child.legacyFeatures.addAll(legacyFeatures);
    return child;
  }

  void cancelRead() {
    if (transport instanceof HttpTransport) ((HttpTransport) transport).cancel();
  }

  private String requestUrl(String path) {
    String base = Connection.normalizeUrl(connection.url);
    if (!path.startsWith("/api/")
        || path.contains("..")
        || path.contains("\\")
        || path.contains("\r")
        || path.contains("\n")) throw new IllegalArgumentException("只允许本站 /api/ 接口");
    java.net.URI uri = java.net.URI.create(base + path);
    if (uri.getPath().contains("..")
        || !uri.getPath().startsWith(java.net.URI.create(base).getPath() + "/api/"))
      throw new IllegalArgumentException("接口越界");
    return base + path;
  }

  private Map<String, String> requestHeaders(String contentType) {
    Map<String, String> headers = new HashMap<>();
    headers.put("Accept", "application/json");
    if (!connection.apiKey.isBlank()) headers.put("X-API-Key", connection.apiKey);
    else if (!token.isBlank()) headers.put("Authorization", "Bearer " + token);
    if (contentType != null) headers.put("Content-Type", contentType);
    return headers;
  }

  public Transport.Reply request(String path, String method, byte[] body, String contentType)
      throws Exception {
    return transport.send(requestUrl(path), method, requestHeaders(contentType), body);
  }

  public StreamTransport.Stream stream(String path, String lastId) throws Exception {
    String url = requestUrl(path);
    Map<String, String> headers = requestHeaders(null);
    headers.put("Accept", "text/event-stream");
    headers.put("Cache-Control", "no-cache");
    if (lastId != null && !lastId.isEmpty()) {
      if (lastId.length() > 256 || lastId.chars().anyMatch(c -> c < 32 || c == 127))
        throw new IllegalArgumentException("Invalid event ID");
      headers.put("Last-Event-ID", lastId);
    }
    if (!(transport instanceof StreamTransport))
      throw new IOException("Streaming transport unavailable");
    return ((StreamTransport) transport).openStream(url, headers);
  }

  public Transport.Reply execute(
      Endpoint e, JSONObject path, JSONObject query, JSONObject body, byte[] file, String field)
      throws Exception {
    String type = Schema.mediaType(e, spec);
    byte[] bytes = null;
    if (e.mutates()) {
      if (type.equals("multipart/form-data") || file != null) {
        String boundary = "AstrMobile" + UUID.randomUUID().toString().replace("-", "");
        bytes = multipart(body, file, field, boundary);
        type = "multipart/form-data; boundary=" + boundary;
      } else if (type.equals("application/x-www-form-urlencoded")) {
        StringJoiner form = new StringJoiner("&");
        for (Iterator<String> it = body.keys(); it.hasNext(); ) {
          String k = it.next();
          form.add(Endpoint.encode(k) + "=" + Endpoint.encode(body.get(k).toString()));
        }
        bytes = form.toString().getBytes(StandardCharsets.UTF_8);
      } else bytes = body.toString().getBytes(StandardCharsets.UTF_8);
    }
    Transport.Reply reply =
        request(e.resolve(path, query), e.method, bytes, bytes == null ? null : type);
    checkHttp(reply);
    if (reply.contentType.contains("json")) json(reply);
    return reply;
  }

  public static byte[] multipart(JSONObject fields, byte[] file, String field, String boundary)
      throws Exception {
    if (!field.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IllegalArgumentException("无效文件字段名");
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (Iterator<String> it = fields.keys(); it.hasNext(); ) {
      String k = it.next();
      if (!k.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IllegalArgumentException("无效表单字段名");
      out.write(
          ("--"
                  + boundary
                  + "\r\nContent-Disposition: form-data; name=\""
                  + k
                  + "\"\r\n\r\n"
                  + fields.get(k)
                  + "\r\n")
              .getBytes(StandardCharsets.UTF_8));
    }
    if (file != null) {
      out.write(
          ("--"
                  + boundary
                  + "\r\nContent-Disposition: form-data; name=\""
                  + field
                  + "\"; filename=\"upload.bin\"\r\nContent-Type: application/octet-stream\r\n\r\n")
              .getBytes(StandardCharsets.UTF_8));
      out.write(file);
      out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }
    out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
    return out.toByteArray();
  }

  public static void checkHttp(Transport.Reply r) throws ApiException {
    if (r.status < 200 || r.status >= 300)
      throw new ApiException(
          r.status,
          r.status == 401
              ? "登录已过期或凭据无效，请重新连接"
              : r.status == 403
                  ? "权限不足，请检查 API Key scope"
                  : r.status == 404 ? "服务器版本不提供此接口" : "HTTP " + r.status + "（请检查服务器日志）");
  }

  public static JSONObject json(Transport.Reply r) throws Exception {
    checkHttp(r);
    JSONObject j = new JSONObject(r.text());
    if ("error".equals(j.optString("status")))
      throw new ApiException(r.status, j.optString("message", "操作失败"));
    return j;
  }
}
