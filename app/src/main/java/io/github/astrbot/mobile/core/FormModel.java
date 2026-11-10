package io.github.astrbot.mobile.core;

import java.math.BigDecimal;
import java.util.*;
import org.json.*;

/** A detached, type-preserving draft. Unknown fields survive every edit. */
public final class FormModel {
  public final JSONObject original;
  public final JSONObject draft;

  public FormModel(JSONObject source) throws Exception {
    original = new JSONObject(source.toString());
    draft = new JSONObject(source.toString());
  }

  public Object get(List<String> path) throws Exception {
    Object node = draft;
    for (String key : path)
      node =
          node instanceof JSONArray
              ? ((JSONArray) node).get(Integer.parseInt(key))
              : ((JSONObject) node).get(key);
    return node;
  }

  public void set(List<String> path, Object value) throws Exception {
    if (path.isEmpty()) throw new IllegalArgumentException("不能替换根节点");
    Object parent = get(path.subList(0, path.size() - 1));
    String key = path.get(path.size() - 1);
    if (parent instanceof JSONArray) ((JSONArray) parent).put(Integer.parseInt(key), value);
    else ((JSONObject) parent).put(key, value);
  }

  public List<String> changes() throws Exception {
    JSONArray a =
        CoreRuntime.array("form.diff", CoreRuntime.args("before", original, "after", draft));
    List<String> result = new ArrayList<>();
    for (int i = 0; i < a.length(); i++) result.add(a.getString(i));
    return result;
  }

  public static Object parse(String text, Object before, JSONObject meta) {
    if (before instanceof Boolean) {
      if (!text.equals("true") && !text.equals("false"))
        throw new IllegalArgumentException("请选择开关状态");
      return Boolean.valueOf(text);
    }
    if (before instanceof Number) {
      try {
        BigDecimal n = new BigDecimal(text.trim());
        String type = meta.optString("type");
        if (before instanceof Integer
            || before instanceof Long
            || type.equals("int")
            || type.equals("integer")) n.toBigIntegerExact();
        if (meta.has("minimum") && n.doubleValue() < meta.optDouble("minimum"))
          throw new IllegalArgumentException("数值不能小于 " + meta.opt("minimum"));
        if (meta.has("maximum") && n.doubleValue() > meta.optDouble("maximum"))
          throw new IllegalArgumentException("数值不能大于 " + meta.opt("maximum"));
        return n;
      } catch (NumberFormatException | ArithmeticException e) {
        throw new IllegalArgumentException("请输入符合类型的数字");
      }
    }
    return text;
  }

  public static JSONObject metadata(JSONObject root, List<String> path) {
    return CoreRuntime.object(
        "form.metadata",
        CoreRuntime.args(
            "root", root == null ? new JSONObject() : root, "path", new JSONArray(path)));
  }

  public static Object defaultValue(JSONObject meta) throws Exception {
    return CoreRuntime.call("form.default", CoreRuntime.args("meta", meta));
  }
}
