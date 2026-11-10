package io.github.astrbot.mobile.core;

import java.util.*;
import org.json.*;

public final class Schema {
  private Schema() {}

  public static JSONObject resolve(JSONObject schema, JSONObject spec) {
    String ref = schema.optString("$ref");
    if (!ref.startsWith("#/")) return schema;
    Object node = spec;
    for (String key : ref.substring(2).split("/")) {
      if (!(node instanceof JSONObject)) return schema;
      node = ((JSONObject) node).opt(key.replace("~1", "/").replace("~0", "~"));
    }
    return node instanceof JSONObject ? (JSONObject) node : schema;
  }

  public static Object sample(JSONObject schema, JSONObject spec, int depth) throws Exception {
    if (depth > 7) return JSONObject.NULL;
    schema = resolve(schema, spec);
    if (schema.has("default")) return schema.get("default");
    if (schema.has("example")) return schema.get("example");
    JSONArray e = schema.optJSONArray("enum");
    if (e != null && e.length() > 0) return e.get(0);
    for (String union : List.of("anyOf", "oneOf")) {
      JSONArray a = schema.optJSONArray(union);
      if (a != null)
        for (int i = 0; i < a.length(); i++) {
          JSONObject s = a.getJSONObject(i);
          if (!"null".equals(s.optString("type"))) return sample(s, spec, depth + 1);
        }
    }
    switch (schema.optString("type", schema.has("properties") ? "object" : "string")) {
      case "object":
        JSONObject o = new JSONObject(), properties = schema.optJSONObject("properties");
        if (properties != null)
          for (Iterator<String> i = properties.keys(); i.hasNext(); ) {
            String k = i.next();
            o.put(k, sample(properties.getJSONObject(k), spec, depth + 1));
          }
        return o;
      case "array":
        return new JSONArray();
      case "boolean":
        return false;
      case "integer":
      case "number":
        return 0;
      default:
        return "";
    }
  }

  public static JSONObject requestSchema(Endpoint e, JSONObject spec) {
    JSONObject body = e.definition.optJSONObject("requestBody");
    if (body == null) return new JSONObject();
    body = resolve(body, spec);
    JSONObject content = body.optJSONObject("content");
    if (content == null) return new JSONObject();
    for (String type :
        List.of("application/json", "multipart/form-data", "application/x-www-form-urlencoded")) {
      JSONObject media = content.optJSONObject(type);
      if (media != null)
        return resolve(
            media.optJSONObject("schema") == null
                ? new JSONObject()
                : media.optJSONObject("schema"),
            spec);
    }
    return new JSONObject();
  }

  public static String mediaType(Endpoint e, JSONObject spec) {
    JSONObject body = e.definition.optJSONObject("requestBody");
    if (body == null) return "application/json";
    JSONObject c = resolve(body, spec).optJSONObject("content");
    if (c == null) return "application/json";
    for (String type :
        List.of(
            "application/json",
            "multipart/form-data",
            "application/x-www-form-urlencoded",
            "application/octet-stream")) if (c.has(type)) return type;
    return "application/json";
  }
}
