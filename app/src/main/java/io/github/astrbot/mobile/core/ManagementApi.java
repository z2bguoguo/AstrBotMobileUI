package io.github.astrbot.mobile.core;

import java.nio.charset.StandardCharsets;
import org.json.*;

/** User-facing management workflows with explicit, tested legacy request shapes. */
public final class ManagementApi {
  public enum Resource {
    PERSONAS("人格与提示词", "personas", "/api/persona/list"),
    MCP("MCP 服务", "mcp/servers", "/api/tools/mcp/servers"),
    PLUGINS("插件管理", "plugins", "/api/plugin/get"),
    PROVIDERS("模型服务", "providers", "/api/config/provider/list"),
    BOTS("机器人平台", "bots", "/api/config/platform/list");
    public final String title, path, legacy;

    Resource(String title, String path, String legacy) {
      this.title = title;
      this.path = path;
      this.legacy = legacy;
    }
  }

  private final ApiClient client;

  public ManagementApi(ApiClient client) {
    this.client = client;
  }

  private Object call(String path, String method, JSONObject body) throws Exception {
    JSONObject reply =
        ApiClient.json(
            client.request(
                path,
                method,
                body == null ? null : body.toString().getBytes(StandardCharsets.UTF_8),
                body == null ? null : "application/json"));
    Object data = reply.opt("data");
    return data == null || data == JSONObject.NULL ? new JSONObject() : data;
  }

  private String choose(String modern, String old) {
    return client.modern ? modern : old;
  }

  public Object overview() throws Exception {
    return call(choose("/api/v1/stats", "/api/stat/get"), "GET", null);
  }

  public JSONObject configuration(String id) throws Exception {
    return (JSONObject)
        call(
            choose(
                id.equals("system")
                    ? "/api/v1/system-config"
                    : "/api/v1/config-profiles/" + Endpoint.encode(id),
                id.equals("system")
                    ? "/api/config/get"
                    : "/api/config/abconf?id=" + Endpoint.encode(id)),
            "GET",
            null);
  }

  public void saveConfiguration(String id, JSONObject config) throws Exception {
    call(
        choose(
            id.equals("system")
                ? "/api/v1/system-config"
                : "/api/v1/config-profiles/" + Endpoint.encode(id),
            "/api/config/astrbot/update"),
        client.modern ? "PUT" : "POST",
        client.modern
            ? config
            : new JSONObject()
                .put("conf_id", id.equals("system") ? "default" : id)
                .put("config", config));
  }

  public JSONArray profiles() throws Exception {
    Object o = call(choose("/api/v1/config-profiles", "/api/config/abconfs"), "GET", null);
    return array(o, "info_list");
  }

  public JSONArray list(Resource resource) throws Exception {
    Object data = call(choose("/api/v1/" + resource.path, resource.legacy), "GET", null);
    return array(
        data,
        resource == Resource.PROVIDERS
            ? "providers"
            : resource == Resource.BOTS ? "bots" : "plugins");
  }

  public static JSONArray array(Object data, String key) throws Exception {
    if (data instanceof JSONArray) return (JSONArray) data;
    if (data instanceof JSONObject) {
      JSONObject o = (JSONObject) data;
      JSONArray list = o.optJSONArray(key);
      if (list != null) return list;
      if (key.equals("bots") && o.optJSONArray("platforms") != null)
        return o.getJSONArray("platforms");
    }
    throw new IllegalArgumentException("服务器返回的列表格式暂不支持，请使用高级工具并反馈版本");
  }

  public static String id(Resource resource, JSONObject item) {
    return item.optString(
        resource == Resource.PERSONAS
            ? "persona_id"
            : resource == Resource.MCP || resource == Resource.PLUGINS ? "name" : "id");
  }

  public JSONObject detail(Resource resource, JSONObject item) throws Exception {
    String id = id(resource, item), encoded = Endpoint.encode(id);
    if (resource == Resource.PLUGINS)
      return (JSONObject)
          call(
              choose(
                  "/api/v1/plugins/" + encoded + "/config",
                  "/api/config/get?plugin_name=" + encoded),
              "GET",
              null);
    JSONObject config = new JSONObject(item.toString());
    if (resource == Resource.PERSONAS) {
      Object data =
          call(
              choose("/api/v1/personas/by-id?persona_id=" + encoded, "/api/persona/detail"),
              client.modern ? "GET" : "POST",
              client.modern ? null : new JSONObject().put("persona_id", id));
      config = (JSONObject) data;
    }
    if (resource == Resource.PROVIDERS && client.modern) {
      JSONObject data =
          (JSONObject) call("/api/v1/providers/by-id?provider_id=" + encoded, "GET", null);
      config = data.getJSONObject("provider");
    }
    if (resource == Resource.BOTS && client.modern) {
      JSONObject data = (JSONObject) call("/api/v1/bots/by-id?bot_id=" + encoded, "GET", null);
      config = data.getJSONObject("bot");
    }
    if (resource == Resource.MCP)
      for (String key : new String[] {"name", "connected", "tools", "errlogs"}) config.remove(key);
    return new JSONObject().put("config", config).put("metadata", new JSONObject());
  }

  public void save(Resource type, String id, JSONObject config, boolean create) throws Exception {
    if (type == Resource.PERSONAS) {
      JSONObject b = new JSONObject(config.toString()).put("persona_id", id);
      call(
          choose(
              create ? "/api/v1/personas" : "/api/v1/personas/by-id",
              create ? "/api/persona/create" : "/api/persona/update"),
          client.modern ? (create ? "POST" : "PUT") : "POST",
          b);
      return;
    }
    if (type == Resource.PLUGINS) {
      call(
          choose(
              "/api/v1/plugins/" + Endpoint.encode(id) + "/config",
              "/api/config/plugin/update?plugin_name=" + Endpoint.encode(id)),
          client.modern ? "PUT" : "POST",
          client.modern ? new JSONObject().put("config", config) : config);
      return;
    }
    if (type == Resource.MCP) {
      JSONObject b =
          create
              ? new JSONObject(config.toString()).put("name", id)
              : new JSONObject()
                  .put(client.modern ? "server_name" : "name", id)
                  .put("config", config);
      call(
          choose(
              create ? "/api/v1/mcp/servers" : "/api/v1/mcp/servers/by-name",
              create ? "/api/tools/mcp/add" : "/api/tools/mcp/update"),
          client.modern && !create ? "PUT" : "POST",
          b);
      return;
    }
    String name = type == Resource.BOTS ? "bots" : "providers",
        old = type == Resource.BOTS ? "platform" : "provider";
    call(
        choose("/api/v1/" + name + "/by-id", "/api/config/" + old + "/update"),
        client.modern ? "PUT" : "POST",
        new JSONObject()
            .put(client.modern ? (type == Resource.BOTS ? "bot_id" : "provider_id") : "id", id)
            .put("config", config));
  }

  public void pluginEnabled(String id, boolean enabled) throws Exception {
    call(
        choose(
            "/api/v1/plugins/" + Endpoint.encode(id) + "/enabled",
            "/api/plugin/" + (enabled ? "on" : "off")),
        client.modern ? "PATCH" : "POST",
        client.modern
            ? new JSONObject().put("enabled", enabled)
            : new JSONObject().put("name", id));
  }
}
