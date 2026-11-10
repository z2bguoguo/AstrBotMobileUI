package io.github.astrbot.mobile.core;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Native transport facade over the replaceable JSON script core. */
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

  private Object call(
      String op, Resource type, String id, JSONObject config, boolean create, boolean enabled)
      throws Exception {
    String family =
        type == null ? (op.equals("saveConfiguration") ? "configuration" : op) : type.name();
    boolean modern = client.modern && !client.legacyFeatures.contains(family);
    JSONObject args =
        CoreRuntime.args(
            "op",
            op,
            "modern",
            modern,
            "resource",
            type == null ? "" : type.name(),
            "id",
            id,
            "config",
            config == null ? new JSONObject() : config,
            "create",
            create,
            "enabled",
            enabled);
    JSONObject plan = CoreRuntime.object("management.plan", args);
    String path = plan.getString("path"), method = plan.getString("method");
    Transport.Reply reply = request(plan);
    // Only read-only 404/405 can establish a legacy family. Never replay a mutation.
    if (modern
        && method.equals("GET")
        && (reply.status == 404 || reply.status == 405)
        && !plan.isNull("legacy")) {
      args.put("modern", false);
      plan = CoreRuntime.object("management.plan", args);
      reply = request(plan);
      ApiClient.checkHttp(reply);
      client.legacyFeatures.add(family);
    }
    if (!client.connection.apiKey.isBlank()
        && !plan.getString("path").startsWith("/api/v1/")
        && reply.status == 401)
      throw new ApiClient.ApiException(401, "此旧版接口需要 WebUI 账户登录，API Key 无法访问；请切换连接的认证方式或升级服务器。");
    JSONObject envelope = ApiClient.json(reply);
    Object data = envelope.opt("data");
    return data == null || data == JSONObject.NULL ? new JSONObject() : data;
  }

  private Transport.Reply request(JSONObject plan) throws Exception {
    Object body = plan.opt("body");
    return client.request(
        plan.getString("path"),
        plan.getString("method"),
        body == null || body == JSONObject.NULL
            ? null
            : body.toString().getBytes(StandardCharsets.UTF_8),
        body == null || body == JSONObject.NULL ? null : "application/json");
  }

  public Object overview() throws Exception {
    try {
      return call("overview", null, "", null, false, false);
    } catch (ApiClient.ApiException e) {
      if (e.status == 403)
        return new JSONObject()
            .put(
                "notice",
                "运行统计需要 API Key 的 system 权限。请在 AstrBot WebUI 编辑该 Key 的授权范围，再重新连接；其他已授权功能仍可使用。");
      if (e.status == 404 || e.status == 405)
        return new JSONObject().put("notice", "此服务器未提供运行统计接口，连接仍然可用。");
      throw e;
    }
  }

  public JSONObject pluginPage(String plugin, String view) throws Exception {
    String modern =
        "/api/v1/plugins/" + Endpoint.encode(plugin) + "/views/" + Endpoint.encode(view);
    String old =
        "/api/plugin/page/entry?name=" + Endpoint.encode(plugin) + "&page=" + Endpoint.encode(view);
    Transport.Reply reply = client.request(client.modern ? modern : old, "GET", null, null);
    if (client.modern && (reply.status == 404 || reply.status == 405)) {
      reply = client.request(modern.replace("/views/", "/pages/"), "GET", null, null);
      if (reply.status == 404 || reply.status == 405)
        reply = client.request(old, "GET", null, null);
    }
    return ApiClient.json(reply);
  }

  public JSONObject configuration(String id) throws Exception {
    return (JSONObject) call("configuration", null, id, null, false, false);
  }

  public void saveConfiguration(String id, JSONObject config) throws Exception {
    call("saveConfiguration", null, id, config, false, false);
  }

  public JSONArray profiles() throws Exception {
    return array(call("profiles", null, "", null, false, false), "info_list");
  }

  public JSONArray list(Resource r) throws Exception {
    return array(
        call("list", r, "", null, false, false),
        r == Resource.PROVIDERS ? "providers" : r == Resource.BOTS ? "bots" : "plugins");
  }

  public static JSONArray array(Object data, String key) {
    return CoreRuntime.array("management.array", CoreRuntime.args("data", data, "key", key));
  }

  public static String id(Resource resource, JSONObject item) {
    return CoreRuntime.text(
        "management.id", CoreRuntime.args("resource", resource.name(), "item", item));
  }

  public JSONObject detail(Resource r, JSONObject item) throws Exception {
    if (r == Resource.MCP || ((r == Resource.BOTS || r == Resource.PROVIDERS) && !client.modern))
      return CoreRuntime.object(
          "management.detail", CoreRuntime.args("resource", r.name(), "item", item));
    JSONObject data = (JSONObject) call("detail", r, id(r, item), null, false, false);
    if (r == Resource.PLUGINS) return data;
    JSONObject config =
        r == Resource.PROVIDERS
            ? data.getJSONObject("provider")
            : r == Resource.BOTS ? data.getJSONObject("bot") : data;
    return new JSONObject().put("config", config).put("metadata", new JSONObject());
  }

  public void save(Resource r, String id, JSONObject config, boolean create) throws Exception {
    call("save", r, id, config, create, false);
  }

  public void pluginEnabled(String id, boolean enabled) throws Exception {
    call("pluginEnabled", Resource.PLUGINS, id, null, false, enabled);
  }
}
