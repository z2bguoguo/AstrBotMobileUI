package io.github.astrbot.mobile.core;

import java.math.*;
import java.util.*;
import org.json.*;

/** Preserve numbers outside JavaScript's exact numeric range across the JSON boundary. */
final class ScriptJson {
  static final String NUMBER = "__astrbot_exact_number__", ESCAPE = "__astrbot_escaped_object__";

  static Object encode(Object v) throws JSONException {
    if (v instanceof Number) {
      BigDecimal n = new BigDecimal(v.toString());
      if (n.precision() > 15 || n.abs().compareTo(new BigDecimal("9007199254740991")) > 0)
        return new JSONObject().put(NUMBER, v.toString());
    }
    if (v instanceof JSONObject) {
      JSONObject src = (JSONObject) v, out = new JSONObject();
      for (Iterator<String> i = src.keys(); i.hasNext(); ) {
        String k = i.next();
        out.put(k, encode(src.get(k)));
      }
      return src.has(NUMBER) || src.has(ESCAPE) ? new JSONObject().put(ESCAPE, out) : out;
    }
    if (v instanceof JSONArray) {
      JSONArray out = new JSONArray(), a = (JSONArray) v;
      for (int i = 0; i < a.length(); i++) out.put(encode(a.get(i)));
      return out;
    }
    return v;
  }

  static Object decode(Object v) throws JSONException {
    if (v instanceof JSONObject) {
      JSONObject src = (JSONObject) v;
      if (src.length() == 1 && src.has(NUMBER)) return new BigDecimal(src.getString(NUMBER));
      boolean escaped = src.length() == 1 && src.has(ESCAPE);
      if (escaped) src = src.getJSONObject(ESCAPE);
      JSONObject out = new JSONObject();
      for (Iterator<String> i = src.keys(); i.hasNext(); ) {
        String k = i.next();
        out.put(k, decode(src.get(k)));
      }
      return out;
    }
    if (v instanceof JSONArray) {
      JSONArray out = new JSONArray(), a = (JSONArray) v;
      for (int i = 0; i < a.length(); i++) out.put(decode(a.get(i)));
      return out;
    }
    return v;
  }

  private ScriptJson() {}
}
