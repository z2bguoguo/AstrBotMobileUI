package io.github.astrbot.mobile.core;

import java.net.URI;
import java.util.UUID;
import org.json.JSONObject;

public final class Connection {
  public String id = UUID.randomUUID().toString();
  public String name = "AstrBot", group = "默认", url = "", username = "astrbot", password = "";
  public String apiKey = "", passwordMode = "plain";

  public static String normalizeUrl(String value) {
    URI uri = URI.create(value.trim());
    if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
        || uri.getHost() == null
        || uri.getUserInfo() != null
        || uri.getQuery() != null
        || uri.getFragment() != null)
      throw new IllegalArgumentException("需要 http(s) 地址，不能含账户、查询或片段");
    String normalized = uri.toString();
    while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
    return normalized;
  }

  public JSONObject toJson() throws Exception {
    return new JSONObject()
        .put("id", id)
        .put("name", name)
        .put("group", group)
        .put("url", url)
        .put("username", username)
        .put("password", password)
        .put("apiKey", apiKey)
        .put("passwordMode", passwordMode);
  }

  public static Connection fromJson(JSONObject j) {
    Connection c = new Connection();
    c.id = j.optString("id", c.id);
    c.name = j.optString("name", "AstrBot");
    c.group = j.optString("group", "默认");
    c.url = j.optString("url");
    c.username = j.optString("username", "astrbot");
    c.password = j.optString("password");
    c.apiKey = j.optString("apiKey");
    c.passwordMode = j.optString("passwordMode", "plain");
    return c;
  }

  public Connection duplicate() throws Exception {
    Connection c = fromJson(toJson());
    c.id = UUID.randomUUID().toString();
    c.name += " 副本";
    return c;
  }
}
