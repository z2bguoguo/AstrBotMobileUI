package io.github.astrbot.mobile.core;

import static org.junit.Assert.*;

import java.util.*;
import org.json.*;
import org.junit.Test;

public class ManagementApiTest {
  private ApiClient client(ApiClientTest.Fake transport, boolean modern) {
    Connection c = new Connection();
    c.url = "https://example.com";
    ApiClient api = new ApiClient(c, transport);
    api.modern = modern;
    return api;
  }

  @Test
  public void modernConfigSavesFullDraftNotMetadata() throws Exception {
    ApiClientTest.Fake f = new ApiClientTest.Fake().reply(200, "{}");
    new ManagementApi(client(f, true))
        .saveConfiguration(
            "system", new JSONObject().put("log_level", "INFO").put("untouched", true));
    assertEquals("PUT", f.methods.get(0));
    assertTrue(f.urls.get(0).endsWith("/api/v1/system-config"));
    JSONObject b = new JSONObject(f.bodies.get(0));
    assertTrue(b.getBoolean("untouched"));
    assertFalse(b.has("config"));
  }

  @Test
  public void legacyConfigHasExplicitWrapper() throws Exception {
    ApiClientTest.Fake f = new ApiClientTest.Fake().reply(200, "{}");
    new ManagementApi(client(f, false))
        .saveConfiguration("system", new JSONObject().put("enabled", false));
    JSONObject b = new JSONObject(f.bodies.get(0));
    assertEquals("default", b.getString("conf_id"));
    assertFalse(b.getJSONObject("config").getBoolean("enabled"));
    assertEquals(1, f.urls.size());
  }

  @Test
  public void profileIdIsEncoded() throws Exception {
    ApiClientTest.Fake f = new ApiClientTest.Fake().reply(200, "{\"data\":{\"config\":{}}}");
    new ManagementApi(client(f, true)).configuration("group/a");
    assertTrue(f.urls.get(0).endsWith("config-profiles/group%2Fa"));
  }

  @Test
  public void oldPersonaDetailUsesBodyAndPreservesPrompt() throws Exception {
    ApiClientTest.Fake f =
        new ApiClientTest.Fake()
            .reply(200, "{\"data\":{\"persona_id\":\"p\",\"system_prompt\":\"full prompt\"}}");
    JSONObject doc =
        new ManagementApi(client(f, false))
            .detail(ManagementApi.Resource.PERSONAS, new JSONObject().put("persona_id", "p"));
    assertEquals("POST", f.methods.get(0));
    assertEquals("p", new JSONObject(f.bodies.get(0)).getString("persona_id"));
    assertEquals("full prompt", doc.getJSONObject("config").getString("system_prompt"));
  }

  @Test
  public void mcpRuntimeFieldsAreNotWrittenBack() throws Exception {
    ApiClientTest.Fake f = new ApiClientTest.Fake();
    JSONObject item =
        new JSONObject(
            "{\"name\":\"tools\",\"connected\":true,\"tools\":[\"one\"],\"errlogs\":\"log\",\"url\":\"https://example.com/mcp\",\"headers\":{\"Authorization\":\"fixture\"}}");
    JSONObject c =
        new ManagementApi(client(f, true))
            .detail(ManagementApi.Resource.MCP, item)
            .getJSONObject("config");
    assertFalse(c.has("connected"));
    assertFalse(c.has("tools"));
    assertTrue(item.has("tools"));
    assertEquals("fixture", c.getJSONObject("headers").getString("Authorization"));
  }

  @Test
  public void newMcpConfigIsFlatForAstrBotAddContract() throws Exception {
    ApiClientTest.Fake f = new ApiClientTest.Fake().reply(200, "{}");
    new ManagementApi(client(f, true))
        .save(
            ManagementApi.Resource.MCP,
            "new",
            new JSONObject().put("url", "https://example.com/mcp"),
            true);
    JSONObject b = new JSONObject(f.bodies.get(0));
    assertEquals("new", b.getString("name"));
    assertEquals("https://example.com/mcp", b.getString("url"));
    assertFalse(b.has("config"));
  }

  @Test
  public void mcpUpdateUsesNameAndConfigWithoutRetry() throws Exception {
    ApiClientTest.Fake f = new ApiClientTest.Fake().reply(200, "{}");
    new ManagementApi(client(f, true))
        .save(ManagementApi.Resource.MCP, "tools", new JSONObject().put("active", false), false);
    JSONObject b = new JSONObject(f.bodies.get(0));
    assertEquals("tools", b.getString("server_name"));
    assertFalse(b.getJSONObject("config").getBoolean("active"));
    assertEquals("PUT", f.methods.get(0));
  }

  @Test
  public void pluginConfigLegacyHasNoExtraConfigWrapper() throws Exception {
    ApiClientTest.Fake f = new ApiClientTest.Fake().reply(200, "{}");
    new ManagementApi(client(f, false))
        .save(
            ManagementApi.Resource.PLUGINS, "plugin", new JSONObject().put("enabled", true), false);
    assertTrue(f.urls.get(0).endsWith("/api/config/plugin/update?plugin_name=plugin"));
    assertTrue(new JSONObject(f.bodies.get(0)).getBoolean("enabled"));
  }

  @Test
  public void pluginEnabledUsesNativeBooleanContract() throws Exception {
    ApiClientTest.Fake f = new ApiClientTest.Fake().reply(200, "{}");
    new ManagementApi(client(f, true)).pluginEnabled("plugin", false);
    assertEquals("PATCH", f.methods.get(0));
    assertFalse(new JSONObject(f.bodies.get(0)).getBoolean("enabled"));
  }

  @Test
  public void forbiddenWriteIsNotRetried() throws Exception {
    ApiClientTest.Fake f = new ApiClientTest.Fake().reply(403, "{}");
    assertThrows(
        ApiClient.ApiException.class,
        () -> new ManagementApi(client(f, true)).saveConfiguration("system", new JSONObject()));
    assertEquals(1, f.urls.size());
  }

  @Test
  public void malformedListsFailClearly() {
    assertThrows(
        IllegalArgumentException.class, () -> ManagementApi.array(new JSONObject(), "plugins"));
  }

  @Test
  public void modelAndPlatformListWrappersAccepted() throws Exception {
    assertEquals(
        1, ManagementApi.array(new JSONObject("{\"providers\":[{}]}"), "providers").length());
    assertEquals(1, ManagementApi.array(new JSONObject("{\"platforms\":[{}]}"), "bots").length());
  }
}
