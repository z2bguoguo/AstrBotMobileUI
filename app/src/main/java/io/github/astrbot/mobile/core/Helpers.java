package io.github.astrbot.mobile.core;

import java.util.*;
import java.util.regex.Pattern;
import org.json.*;

public final class Helpers {
  private Helpers() {}

  private static final Pattern SECRET =
      Pattern.compile("(?i).*(password|passwd|token|secret|api.?key|authorization|cookie).*");

  public static Object redact(Object value) throws Exception {
    if (value instanceof JSONObject) {
      JSONObject j = (JSONObject) value, o = new JSONObject();
      for (Iterator<String> it = j.keys(); it.hasNext(); ) {
        String k = it.next();
        o.put(k, SECRET.matcher(k).matches() ? "" : redact(j.get(k)));
      }
      return o;
    }
    if (value instanceof JSONArray) {
      JSONArray a = (JSONArray) value, b = new JSONArray();
      for (int i = 0; i < a.length(); i++) b.put(redact(a.get(i)));
      return b;
    }
    return value;
  }

  public static List<String> warnings(String name, String value) {
    List<String> result = new ArrayList<>();
    String k = name.toLowerCase(Locale.ROOT);
    if ((k.contains("group") || k.contains("umo") || k.contains("session"))
        && value.matches("[0-9]+")) result.add("部分接口需要平台前缀、group: 或完整 UMO；请以本服务器字段说明为准");
    if (k.contains("url") && !value.isBlank() && !value.matches("https?://.+"))
      result.add("此字段可能需要完整 http(s) 地址");
    return result;
  }

  public static void collectSuggestions(Object data, Set<String> output, int depth)
      throws Exception {
    if (depth > 8 || output.size() > 300) return;
    if (data instanceof JSONObject) {
      JSONObject o = (JSONObject) data;
      for (Iterator<String> it = o.keys(); it.hasNext(); ) {
        String k = it.next();
        Object v = o.get(k);
        if (SECRET.matcher(k).matches()) continue;
        if ((k.equals("id")
                || k.equals("name")
                || k.contains("group_id")
                || k.contains("umo")
                || k.contains("model"))
            && v instanceof String
            && !((String) v).isBlank()) output.add((String) v);
        if (v instanceof JSONObject || v instanceof JSONArray)
          collectSuggestions(v, output, depth + 1);
      }
    } else if (data instanceof JSONArray) {
      JSONArray a = (JSONArray) data;
      for (int i = 0; i < a.length(); i++) collectSuggestions(a.get(i), output, depth + 1);
    }
  }

  public static JSONObject preset(
      Endpoint e, JSONObject path, JSONObject query, JSONObject body, boolean includeSecrets)
      throws Exception {
    JSONObject p =
        new JSONObject()
            .put("path", e.path)
            .put("method", e.method)
            .put("pathParams", path)
            .put("query", query)
            .put("body", body);
    return includeSecrets ? new JSONObject(p.toString()) : (JSONObject) redact(p);
  }

  public static String pretty(Object v) throws Exception {
    if (v instanceof JSONObject) return ((JSONObject) v).toString(2);
    if (v instanceof JSONArray) return ((JSONArray) v).toString(2);
    return String.valueOf(v);
  }
}
