package io.github.astrbot.mobile.ui;

import io.github.astrbot.mobile.core.ApiClient;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class PluginSessions {
  private static final ConcurrentHashMap<String, ApiClient> CLIENTS = new ConcurrentHashMap<>();

  private PluginSessions() {}

  static String add(ApiClient client) {
    String id = UUID.randomUUID().toString();
    CLIENTS.put(id, client);
    return id;
  }

  static ApiClient get(String id) {
    return id == null ? null : CLIENTS.get(id);
  }

  static void remove(String id) {
    if (id != null) CLIENTS.remove(id);
  }
}
