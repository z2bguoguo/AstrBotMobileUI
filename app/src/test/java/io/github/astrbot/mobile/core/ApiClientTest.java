package io.github.astrbot.mobile.core;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;
import org.junit.Test;

public class ApiClientTest {
  static class Fake implements Transport {
    final Queue<Reply> replies = new ArrayDeque<>();
    final List<String> urls = new ArrayList<>(),
        methods = new ArrayList<>(),
        bodies = new ArrayList<>();
    Map<String, String> headers;

    Fake reply(int code, String body) {
      replies.add(new Reply(code, body.getBytes(StandardCharsets.UTF_8), "application/json"));
      return this;
    }

    public Reply send(String url, String method, Map<String, String> headers, byte[] body) {
      urls.add(url);
      methods.add(method);
      this.headers = headers;
      bodies.add(body == null ? "" : new String(body, StandardCharsets.UTF_8));
      if (replies.isEmpty()) throw new AssertionError("Unexpected retry");
      return replies.remove();
    }
  }

  Connection connection() {
    Connection c = new Connection();
    c.url = "https://bot.example/proxy";
    c.password = "secret-value";
    return c;
  }

  JSONObject empty() {
    return new JSONObject();
  }

  Endpoint endpoint(String method) {
    return new Endpoint("/api/v1/example", method, empty());
  }

  @Test
  public void discoversActualServerContract() throws Exception {
    Fake f = new Fake().reply(200, "{\"paths\":{\"/api/v1/custom\":{}}}");
    ApiClient a = new ApiClient(connection(), f);
    a.discover(empty());
    assertTrue(a.modern);
    assertTrue(a.spec.getJSONObject("paths").has("/api/v1/custom"));
  }

  @Test
  public void missingContractUsesLegacy() throws Exception {
    Fake f = new Fake().reply(404, "{}");
    ApiClient a = new ApiClient(connection(), f);
    a.discover(empty());
    assertFalse(a.modern);
    assertEquals(1, f.urls.size());
  }

  @Test
  public void methodNotAllowedContractUsesLegacy() throws Exception {
    ApiClient a = new ApiClient(connection(), new Fake().reply(405, "{}"));
    a.discover(empty());
    assertFalse(a.modern);
  }

  @Test
  public void serverFailureDoesNotDowngrade() throws Exception {
    Fake f = new Fake().reply(500, "{}");
    try {
      new ApiClient(connection(), f).discover(empty());
      fail();
    } catch (ApiClient.ApiException e) {
      assertEquals(500, e.status);
    }
    assertEquals(1, f.urls.size());
  }

  @Test
  public void proxyHtmlDoesNotBecomeLegacy() throws Exception {
    try {
      new ApiClient(connection(), new Fake().reply(200, "<html>oops</html>")).discover(empty());
      fail();
    } catch (JSONException expected) {
    }
  }

  @Test
  public void modernLoginSendsPlaintextProtocolOverHttps() throws Exception {
    Fake f = new Fake().reply(200, "{\"data\":{\"token\":\"jwt\"}}").reply(200, "{}");
    ApiClient a = new ApiClient(connection(), f);
    a.modern = true;
    a.login("123456");
    a.request("/api/v1/stats", "GET", null, null);
    JSONObject body = new JSONObject(f.bodies.get(0));
    assertEquals("secret-value", body.getString("password"));
    assertEquals("123456", body.getString("code"));
    assertEquals("Bearer jwt", f.headers.get("Authorization"));
    assertTrue(f.urls.get(0).endsWith("/proxy/api/v1/auth/login"));
  }

  @Test
  public void oldLoginCanUseMd5Explicitly() throws Exception {
    Connection c = connection();
    c.password = "abc";
    c.passwordMode = "md5";
    Fake f = new Fake().reply(200, "{\"data\":{\"token\":\"jwt\"}}");
    new ApiClient(c, f).login("");
    assertEquals(
        "900150983cd24fb0d6963f7d28e17f72", new JSONObject(f.bodies.get(0)).getString("password"));
    assertTrue(f.urls.get(0).endsWith("/api/auth/login"));
  }

  @Test
  public void twoFactorDoesNotPretendSuccess() throws Exception {
    Fake f = new Fake().reply(200, "{\"data\":{\"totp_required\":true}}");
    try {
      new ApiClient(connection(), f).login("");
      fail();
    } catch (ApiClient.ApiException e) {
      assertEquals(401, e.status);
    }
  }

  @Test
  public void authErrorIsNotRetried() throws Exception {
    Fake f = new Fake().reply(401, "{}");
    try {
      new ApiClient(connection(), f).login("");
      fail();
    } catch (ApiClient.ApiException e) {
      assertEquals(401, e.status);
    }
    assertEquals(1, f.urls.size());
  }

  @Test
  public void apiKeySkipsPasswordAndUsesScopedHeader() throws Exception {
    Connection c = connection();
    c.apiKey = "test-key";
    Fake f = new Fake().reply(200, "{}");
    ApiClient a = new ApiClient(c, f);
    a.login("");
    a.request("/api/v1/stats", "GET", null, null);
    assertEquals(1, f.urls.size());
    assertEquals("test-key", f.headers.get("X-API-Key"));
    assertFalse(f.headers.containsKey("Authorization"));
  }

  @Test
  public void connectionsDoNotShareToken() throws Exception {
    Fake f = new Fake().reply(200, "{\"data\":{\"token\":\"one\"}}");
    new ApiClient(connection(), f).login("");
    Fake other = new Fake().reply(200, "{}");
    new ApiClient(connection(), other).request("/api/stat/get", "GET", null, null);
    assertFalse(other.headers.containsKey("Authorization"));
  }

  @Test
  public void writeNotReplayedOn404() throws Exception {
    Fake f = new Fake().reply(404, "{}");
    try {
      new ApiClient(connection(), f)
          .execute(endpoint("POST"), empty(), empty(), empty(), null, "file");
      fail();
    } catch (ApiClient.ApiException expected) {
    }
    assertEquals(List.of("POST"), f.methods);
  }

  @Test
  public void envelopeErrorIsFailure() throws Exception {
    Fake f = new Fake().reply(200, "{\"status\":\"error\",\"message\":\"Invalid model\"}");
    try {
      new ApiClient(connection(), f)
          .execute(endpoint("POST"), empty(), empty(), empty(), null, "file");
      fail();
    } catch (ApiClient.ApiException e) {
      assertEquals("Invalid model", e.getMessage());
    }
  }

  @Test
  public void getHasNoRequestBody() throws Exception {
    Fake f = new Fake().reply(200, "{}");
    new ApiClient(connection(), f)
        .execute(endpoint("GET"), empty(), empty(), empty(), null, "file");
    assertEquals("", f.bodies.get(0));
  }

  @Test
  public void binaryResponsePreserved() throws Exception {
    Transport t = (u, m, h, b) -> new Transport.Reply(200, new byte[] {0, 1, 2}, "application/zip");
    assertArrayEquals(
        new byte[] {0, 1, 2},
        new ApiClient(connection(), t)
            .execute(endpoint("GET"), empty(), empty(), empty(), null, "file")
            .body);
  }

  @Test
  public void rejectsExternalEndpoint() throws Exception {
    try {
      new ApiClient(connection(), new Fake())
          .request("https://evil.example/api/x", "GET", null, null);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void rejectsTraversal() throws Exception {
    try {
      new ApiClient(connection(), new Fake()).request("/api/../auth", "GET", null, null);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void multipartPreservesBinaryAndFields() throws Exception {
    String b = "boundary";
    byte[] data =
        ApiClient.multipart(new JSONObject().put("name", "中文"), new byte[] {0, 1, 2}, "file", b);
    String s = new String(data, StandardCharsets.UTF_8);
    assertTrue(s.contains("中文"));
    assertTrue(s.contains("name=\"file\""));
    assertTrue(s.endsWith("--boundary--\r\n"));
  }

  @Test
  public void multipartHeaderInjectionRejected() throws Exception {
    try {
      ApiClient.multipart(empty(), new byte[0], "file\r\nX:bad", "b");
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void scopeFailureActionable() throws Exception {
    try {
      ApiClient.checkHttp(new Transport.Reply(403, new byte[0], ""));
      fail();
    } catch (ApiClient.ApiException e) {
      assertTrue(e.getMessage().contains("scope"));
    }
  }
}
