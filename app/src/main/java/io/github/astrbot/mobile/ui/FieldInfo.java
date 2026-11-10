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
            .append(type);
    if (!hint.isBlank()) text.append("\n\n").append(hint);
    for (String k :
        new String[] {
          "default", "minimum", "maximum", "enum", "options", "pattern", "example", "description"
        }) if (meta.has(k)) text.append("\n").append(k).append(": ").append(meta.opt(k));
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
