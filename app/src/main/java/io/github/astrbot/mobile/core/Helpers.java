package io.github.astrbot.mobile.core;

import java.util.*;
import java.util.regex.Pattern;
import org.json.*;

public final class Helpers {
  private Helpers() {}

  private static final Pattern SECRET =
      Pattern.compile("(?i).*(password|passwd|token|secret|api.?key|authorization|cookie).*");

  public static Object redact(Object value) throws Exception {
    return CoreRuntime.call("helpers.redact", CoreRuntime.args("value", value));
  }

  public static List<String> warnings(String name, String value) {
    JSONArray a =
        CoreRuntime.array("helpers.warnings", CoreRuntime.args("name", name, "value", value));
    List<String> result = new ArrayList<>();
    for (int i = 0; i < a.length(); i++) result.add(a.optString(i));
    return result;
  }

  public static void collectSuggestions(Object data, Set<String> output, int depth)
      throws Exception {
    JSONArray a =
        CoreRuntime.array(
            "helpers.suggestions",
            CoreRuntime.args("value", data, "current", new JSONArray(output), "depth", depth));
    for (int i = 0; i < a.length(); i++) output.add(a.getString(i));
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
