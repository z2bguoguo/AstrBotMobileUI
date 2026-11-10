package io.github.astrbot.mobile.core;

import static org.junit.Assert.*;

import java.io.*;
import java.util.*;
import org.json.*;
import org.junit.Test;

public class ModelTest {
  @Test
  public void proxyBaseRetained() {
    assertEquals("https://example.com/bot", Connection.normalizeUrl(" https://example.com/bot/ "));
  }

  @Test
  public void credentialsInUrlRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () -> Connection.normalizeUrl("https://user:pass@example.com"));
  }

  @Test
  public void nonHttpRejected() {
    assertThrows(
        IllegalArgumentException.class, () -> Connection.normalizeUrl("file:///etc/passwd"));
  }

  @Test
  public void queryInBaseRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () -> Connection.normalizeUrl("https://example.com?token=x"));
  }

  @Test
  public void fragmentInBaseRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () -> Connection.normalizeUrl("https://example.com/#/login"));
  }

  @Test
  public void duplicateHasIndependentIdentity() throws Exception {
    Connection a = new Connection();
    a.group = "Production";
    a.password = "pw";
    Connection b = a.duplicate();
    assertNotEquals(a.id, b.id);
    assertEquals(a.group, b.group);
    b.password = "changed";
    assertEquals("pw", a.password);
  }

  @Test
  public void roundtripPreservesConnection() throws Exception {
    Connection a = new Connection();
    a.url = "https://host";
    a.passwordMode = "md5";
    Connection b = Connection.fromJson(a.toJson());
    assertEquals(a.id, b.id);
    assertEquals("md5", b.passwordMode);
  }

  @Test
  public void pathSegmentAndQueryEncoded() throws Exception {
    Endpoint e = new Endpoint("/api/v1/personas/{id}", "GET", new JSONObject());
    String result =
        e.resolve(new JSONObject().put("id", "群/a?"), new JSONObject().put("query", "x y&z"));
    assertEquals("/api/v1/personas/%E7%BE%A4%2Fa%3F?query=x%20y%26z", result);
  }

  @Test
  public void missingPathRejected() {
    Endpoint e = new Endpoint("/api/v1/{id}", "GET", new JSONObject());
    assertThrows(
        IllegalArgumentException.class, () -> e.resolve(new JSONObject(), new JSONObject()));
  }

  @Test
  public void dotPathRejected() {
    Endpoint e = new Endpoint("/api/{id}", "GET", new JSONObject());
    assertThrows(
        IllegalArgumentException.class,
        () -> e.resolve(new JSONObject().put("id", ".."), new JSONObject()));
  }

  @Test
  public void repeatedQueryArraysSupported() throws Exception {
    Endpoint e = new Endpoint("/api/list", "GET", new JSONObject());
    assertEquals(
        "/api/list?id=a&id=b",
        e.resolve(new JSONObject(), new JSONObject().put("id", new JSONArray().put("a").put("b"))));
  }

  @Test
  public void specRetainsPathLevelParameters() throws Exception {
    JSONObject spec =
        new JSONObject(
            "{\"paths\":{\"/api/test\":{\"parameters\":[{\"name\":\"id\"}],\"post\":{\"summary\":\"Save\",\"tags\":[\"Config\"]}}}}");
    Endpoint e = Endpoint.fromSpec(spec).get(0);
    assertEquals("Config", e.category);
    assertEquals("Save", e.title);
    assertEquals(1, e.definition.getJSONArray("parameters").length());
    assertTrue(e.mutates());
  }

  @Test
  public void refSchemaAndEnumDefaults() throws Exception {
    JSONObject spec =
        new JSONObject(
            "{\"components\":{\"schemas\":{\"X\":{\"type\":\"object\",\"properties\":{\"enabled\":{\"type\":\"boolean\"},\"mode\":{\"enum\":[\"group\",\"user\"]}}}}}}");
    JSONObject result =
        (JSONObject) Schema.sample(new JSONObject().put("$ref", "#/components/schemas/X"), spec, 0);
    assertFalse(result.getBoolean("enabled"));
    assertEquals("group", result.getString("mode"));
  }

  @Test
  public void recursiveSchemaBounded() throws Exception {
    JSONObject spec =
        new JSONObject(
            "{\"components\":{\"schemas\":{\"X\":{\"type\":\"object\",\"properties\":{\"child\":{\"$ref\":\"#/components/schemas/X\"}}}}}}");
    assertTrue(
        Schema.sample(new JSONObject().put("$ref", "#/components/schemas/X"), spec, 0)
                .toString()
                .length()
            < 200);
  }

  @Test
  public void optionalUnionChoosesValue() throws Exception {
    Object result =
        Schema.sample(
            new JSONObject("{\"anyOf\":[{\"type\":\"null\"},{\"type\":\"integer\"}]}"),
            new JSONObject(),
            0);
    assertEquals(0, result);
  }

  @Test
  public void responseRedactionRecursive() throws Exception {
    JSONObject source =
        new JSONObject(
            "{\"nested\":[{\"api_key\":\"sensitive\",\"name\":\"safe\"}],\"token\":\"sensitive\"}");
    String result = Helpers.redact(source).toString();
    assertFalse(result.contains("sensitive"));
    assertTrue(result.contains("safe"));
    assertTrue(source.toString().contains("sensitive"));
  }

  @Test
  public void presetsDropSecretsByDefault() throws Exception {
    Endpoint e = new Endpoint("/api/config", "PUT", new JSONObject());
    JSONObject p =
        Helpers.preset(
            e,
            new JSONObject(),
            new JSONObject(),
            new JSONObject().put("password", "secret"),
            false);
    assertFalse(p.toString().contains("secret"));
  }

  @Test
  public void presetsExplicitlyKeepSecrets() throws Exception {
    Endpoint e = new Endpoint("/api/config", "PUT", new JSONObject());
    JSONObject p =
        Helpers.preset(
            e,
            new JSONObject(),
            new JSONObject(),
            new JSONObject().put("password", "secret"),
            true);
    assertTrue(p.toString().contains("secret"));
  }

  @Test
  public void suggestionsNeverCollectSecretIdentifiers() throws Exception {
    Set<String> s = new HashSet<>();
    Helpers.collectSuggestions(
        new JSONObject(
            "{\"id\":\"bot\",\"api_key\":{\"name\":\"private\"},\"nested\":[{\"group_id\":\"123\"}]}"),
        s,
        0);
    assertEquals(Set.of("bot", "123"), s);
  }

  @Test
  public void groupWarningsAdvisory() {
    assertFalse(Helpers.warnings("group_id", "123").isEmpty());
    assertTrue(Helpers.warnings("group_id", "group:123").isEmpty());
    assertTrue(Helpers.warnings("prompt", "123").isEmpty());
  }

  @Test
  public void boundedResponseRejectsOversize() {
    assertThrows(
        IOException.class,
        () -> HttpTransport.readLimited(new ByteArrayInputStream(new byte[10]), 9));
  }

  @Test
  public void boundedResponseAcceptsExactLimit() throws Exception {
    assertEquals(10, HttpTransport.readLimited(new ByteArrayInputStream(new byte[10]), 10).length);
  }

  @Test
  public void pluginScopeCannotEscapeItsNamespace() {
    assertEquals(
        "/api/v1/plugins/extensions/my-plugin/settings/save",
        PluginScope.endpoint("my-plugin", "settings/save", true));
    for (String value :
        List.of(
            "../stats",
            "foo/../bar",
            "foo//bar",
            "foo?api_key=x",
            "https://evil",
            "%2e%2e/foo",
            "foo\\bar"))
      assertThrows(
          IllegalArgumentException.class, () -> PluginScope.endpoint("my-plugin", value, true));
  }

  @Test
  public void legacyPluginScopeIsExplicit() {
    assertEquals("/api/plug/example/config", PluginScope.endpoint("example", "config", false));
  }
}
