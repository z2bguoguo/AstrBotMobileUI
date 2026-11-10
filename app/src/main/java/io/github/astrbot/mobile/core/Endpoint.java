package io.github.astrbot.mobile.core;

import java.net.URLEncoder;
import java.util.*;
import java.util.regex.*;
import org.json.*;

public final class Endpoint {
  public final String path, method, title, category;
  public final JSONObject definition;

  public Endpoint(String path, String method, JSONObject definition) {
    this.path = path;
    this.method = method.toUpperCase(Locale.ROOT);
    this.definition = definition;
    title = definition.optString("summary", method + " " + path);
    JSONArray tags = definition.optJSONArray("tags");
    category = tags == null ? "其他" : tags.optString(0, "其他");
  }

  public boolean mutates() {
    return !method.equals("GET") && !method.equals("HEAD");
  }

  public static String encode(String s) {
    try {
      return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
    } catch (java.io.UnsupportedEncodingException impossible) {
      throw new AssertionError(impossible);
    }
  }

  public String resolve(JSONObject values, JSONObject query) throws Exception {
    return CoreRuntime.text(
        "endpoint.resolve", CoreRuntime.args("path", path, "values", values, "query", query));
  }

  public static List<Endpoint> fromSpec(JSONObject spec) throws Exception {
    List<Endpoint> result = new ArrayList<>();
    JSONArray entries = CoreRuntime.array("endpoint.spec", CoreRuntime.args("spec", spec));
    for (int i = 0; i < entries.length(); i++) {
      JSONObject e = entries.getJSONObject(i);
      result.add(
          new Endpoint(e.getString("path"), e.getString("method"), e.getJSONObject("definition")));
    }
    result.sort(
        Comparator.comparing((Endpoint e) -> e.category)
            .thenComparing(e -> e.path)
            .thenComparing(e -> e.method));
    return result;
  }

  public static List<Endpoint> fromLegacy(JSONArray array) throws Exception {
    List<Endpoint> list = new ArrayList<>();
    for (int i = 0; i < array.length(); i++) {
      JSONObject j = array.getJSONObject(i);
      list.add(new Endpoint(j.getString("path"), j.getString("method"), j));
    }
    return list;
  }
}
