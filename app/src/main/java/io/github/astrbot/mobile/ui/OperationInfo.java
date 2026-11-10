package io.github.astrbot.mobile.ui;

import io.github.astrbot.mobile.core.*;
import org.json.*;

final class OperationInfo {
  static String describe(Endpoint e, JSONObject spec) throws Exception {
    boolean zh = Appearance.language().equals("zh");
    StringBuilder s =
        new StringBuilder(Appearance.text(e.title))
            .append("\n")
            .append(e.method)
            .append(" ")
            .append(e.path);
    s.append(zh ? "\n所需 API Key 权限：" : "\nRequired API key scope: ")
        .append(e.definition.optString("x-astrbot-scope", "WebUI / server policy"));
    s.append(zh ? "\n操作类型：" : "\nOperation: ")
        .append(
            e.mutates()
                ? (zh ? "写入（执行前确认）" : "Write (confirmation required)")
                : (zh ? "只读" : "Read only"));
    String description = e.definition.optString("description");
    if (!description.isBlank()) s.append("\n\n").append(Appearance.text(description));
    JSONArray params = e.definition.optJSONArray("parameters");
    if (params != null)
      for (int i = 0; i < params.length(); i++) {
        JSONObject p = params.getJSONObject(i);
        JSONObject schema = p.optJSONObject("schema");
        s.append("\n\n")
            .append(p.optString("name"))
            .append(" · ")
            .append(p.optString("in"))
            .append(
                p.optBoolean("required")
                    ? (zh ? " · 必填" : " · required")
                    : (zh ? " · 可选" : " · optional"));
        s.append("\n").append(Appearance.text(p.optString("description")));
        if (schema != null)
          s.append("\n").append(FieldInfo.describe(p.optString("name"), "", schema, ""));
      }
    JSONObject body = Schema.requestSchema(e, spec);
    if (body.length() > 0)
      s.append(zh ? "\n\n请求体字段：\n" : "\n\nRequest body schema:\n").append(body.toString(2));
    return s.toString();
  }

  private OperationInfo() {}
}
