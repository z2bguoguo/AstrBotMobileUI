package io.github.astrbot.mobile.core;

/** Restricts HTML bridge calls to one plugin extension namespace. */
public final class PluginScope {
  private PluginScope() {}

  public static String endpoint(String plugin, String endpoint, boolean modern) {
    String trimmed = endpoint.trim().replaceFirst("^/+", "");
    if (plugin.isBlank()
        || trimmed.isBlank()
        || trimmed.contains("\\")
        || trimmed.contains(":")
        || trimmed.contains("?")
        || trimmed.contains("#")) throw new IllegalArgumentException("插件端点不合法");
    StringBuilder path = new StringBuilder(modern ? "/api/v1/plugins/extensions/" : "/api/plug/");
    path.append(Endpoint.encode(plugin));
    for (String segment : trimmed.split("/", -1)) {
      if (segment.isEmpty() || segment.equals(".") || segment.equals("..") || segment.contains("%"))
        throw new IllegalArgumentException("插件端点不能包含路径穿越或预编码片段");
      path.append('/').append(Endpoint.encode(segment));
    }
    return path.toString();
  }
}
