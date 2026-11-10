package io.github.astrbot.mobile.ui;

import org.json.*;

/** Shows server metadata verbatim alongside localized type and format guidance. */
final class FieldInfo {
  static String describe(String key, Object value, JSONObject meta, String hint) {
    String type =
        meta.optString(
            "type",
            value instanceof Boolean
                ? "boolean"
                : value instanceof Number
                    ? "number"
                    : value instanceof JSONArray
                        ? "array"
                        : value instanceof JSONObject ? "object" : "string");
    StringBuilder text =
        new StringBuilder(key)
            .append("\n")
            .append(Appearance.language().equals("zh") ? "类型：" : "Type: ")
            .append(
                Appearance.language().equals("zh")
                    ? java.util.Map.of(
                            "boolean", "开关", "bool", "开关", "integer", "整数", "int", "整数", "number",
                            "数值", "float", "小数", "string", "文本", "array", "列表", "list", "列表",
                            "object", "对象")
                        .getOrDefault(type, type)
                    : type);
    if (!hint.isBlank()) text.append("\n\n").append(hint);
    for (String k :
        new String[] {
          "default", "minimum", "maximum", "enum", "options", "pattern", "example", "description"
        })
      if (meta.has(k)) {
        String caption =
            Appearance.language().equals("zh")
                ? java.util.Map.of(
                        "default",
                        "默认值",
                        "minimum",
                        "最小值",
                        "maximum",
                        "最大值",
                        "enum",
                        "可选值",
                        "options",
                        "可选值",
                        "pattern",
                        "格式规则",
                        "example",
                        "示例",
                        "description",
                        "说明")
                    .getOrDefault(k, k)
                : k;
        Object raw = meta.opt(k);
        JSONObject labels = Appearance.labels();
        if (raw instanceof String && labels != null)
          raw = labels.optString((String) raw, (String) raw);
        text.append("\n").append(caption).append(": ").append(raw);
      }
    if (key.matches(".*(group|session|umo).*"))
      text.append(
          Appearance.language().equals("zh")
              ? "\n群号或会话标识可能需要 group: 或平台前缀。格式提示不阻止保存，请以服务器要求为准。"
              : "\nGroup/session identifiers may need group: or a platform prefix. Format advice does not block saving; follow your server's contract.");
    if (key.matches(".*(password|token|secret|key).*"))
      text.append(
          Appearance.language().equals("zh")
              ? "\n敏感字段。不要在截图、预设或反馈中公开真实值。"
              : "\nSensitive field. Do not publish real values in screenshots, presets or reports.");
    text.append(
        Appearance.language().equals("zh")
            ? "\n\n修改先保存在草稿，点击保存并确认后发送到当前服务器。没有服务器说明的第三方字段保留原名。"
            : "\n\nChanges stay in the draft until you save and confirm. Unknown third-party fields keep their original identifiers.");
    return text.toString();
  }

  private FieldInfo() {}
}
