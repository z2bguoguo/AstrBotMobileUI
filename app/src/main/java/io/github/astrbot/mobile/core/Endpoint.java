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
    Matcher m = Pattern.compile("\\{([^}:]+)(?::[^}]+)?}").matcher(path);
    StringBuffer b = new StringBuffer();
    while (m.find()) {
      String name = m.group(1), value = values.optString(name, "");
      if (value.isBlank()) throw new IllegalArgumentException("缺少路径参数：" + name);
      if (value.equals(".") || value.equals(".."))
        throw new IllegalArgumentException("路径参数不能是 . 或 ..");
      m.appendReplacement(b, Matcher.quoteReplacement(encode(value)));
    }
    m.appendTail(b);
    StringJoiner q = new StringJoiner("&");
    for (Iterator<String> i = query.keys(); i.hasNext(); ) {
      String key = i.next();
      Object value = query.get(key);
      if (value == JSONObject.NULL) continue;
      if (value instanceof JSONArray) {
        JSONArray a = (JSONArray) value;
        for (int j = 0; j < a.length(); j++) q.add(encode(key) + "=" + encode(a.get(j).toString()));
      } else q.add(encode(key) + "=" + encode(value.toString()));
    }
    return b + (q.length() == 0 ? "" : "?" + q);
  }

  public static List<Endpoint> fromSpec(JSONObject spec) throws Exception {
    List<Endpoint> result = new ArrayList<>();
    JSONObject paths = spec.getJSONObject("paths");
    for (Iterator<String> it = paths.keys(); it.hasNext(); ) {
      String path = it.next();
      JSONObject methods = paths.getJSONObject(path);
      for (String method : List.of("get", "post", "put", "patch", "delete", "head"))
        if (methods.has(method)) {
          JSONObject d = new JSONObject(methods.getJSONObject(method).toString());
          JSONArray params = new JSONArray();
          for (JSONArray a :
              Arrays.asList(methods.optJSONArray("parameters"), d.optJSONArray("parameters")))
            if (a != null) for (int k = 0; k < a.length(); k++) params.put(a.get(k));
          d.put("parameters", params);
          result.add(new Endpoint(path, method, d));
        }
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
