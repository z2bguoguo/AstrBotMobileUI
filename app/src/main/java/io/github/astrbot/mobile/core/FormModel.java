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
    List<String> result = new ArrayList<>();
    diff(original, draft, "", result);
    return result;
  }

  private static void diff(Object before, Object after, String path, List<String> changes)
      throws Exception {
    if (before instanceof JSONObject && after instanceof JSONObject) {
      Set<String> keys = new TreeSet<>();
      ((JSONObject) before).keys().forEachRemaining(keys::add);
      ((JSONObject) after).keys().forEachRemaining(keys::add);
      for (String key : keys)
        diff(
            ((JSONObject) before).opt(key),
            ((JSONObject) after).opt(key),
            path.isEmpty() ? key : path + "." + key,
            changes);
    } else if (!Objects.equals(String.valueOf(before), String.valueOf(after))) changes.add(path);
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
    if (root == null || path.isEmpty()) return new JSONObject();
    JSONObject exact = find(root, String.join(".", path), 0);
    if (exact != null) return exact;
    JSONObject scope = root;
    for (int index = 0; index < path.size(); index++) {
      String segment = path.get(index);
      JSONObject field = find(scope, segment, 0);
      if (field == null) return new JSONObject();
      scope = field.optJSONObject("items");
      if (index == path.size() - 1) return field;
      if (scope == null) return new JSONObject();
    }
    return new JSONObject();
  }

  private static JSONObject find(JSONObject o, String key, int depth) {
    if (o == null || depth > 12) return null;
    JSONObject direct = o.optJSONObject(key);
    if (direct != null) return direct;
    for (Iterator<String> it = o.keys(); it.hasNext(); ) {
      String k = it.next();
      if (Set.of("options", "default", "config_template", "condition", "i18n").contains(k))
        continue;
      JSONObject v = o.optJSONObject(k);
      if (v == null) continue;
      JSONObject found = find(v, key, depth + 1);
      if (found != null) return found;
    }
    return null;
  }

  public static Object defaultValue(JSONObject meta) throws Exception {
    if (meta.has("default"))
      return new JSONArray(new JSONArray().put(meta.get("default")).toString()).get(0);
    switch (meta.optString("type", "string")) {
      case "bool":
      case "boolean":
        return false;
      case "int":
      case "integer":
      case "float":
      case "number":
        return 0;
      case "list":
      case "array":
      case "template_list":
        return new JSONArray();
      case "object":
        JSONObject obj = new JSONObject(), items = meta.optJSONObject("items");
        if (items != null)
          for (Iterator<String> it = items.keys(); it.hasNext(); ) {
            String k = it.next();
            JSONObject field = items.optJSONObject(k);
            if (field != null) obj.put(k, defaultValue(field));
          }
        return obj;
      default:
        return "";
    }
  }
}
