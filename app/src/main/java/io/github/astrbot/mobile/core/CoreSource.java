package io.github.astrbot.mobile.core;

import java.util.*;
import org.json.*;

/**
 * Script modules are signed as bundle data and loaded deterministically, without filesystem paths.
 */
public final class CoreSource {
  public static String script(JSONObject payload) throws JSONException {
    JSONObject data = new JSONObject();
    for (Iterator<String> it = payload.keys(); it.hasNext(); ) {
      String key = it.next();
      if (key.endsWith(".json")) data.put(key, new JSONTokener(payload.getString(key)).nextValue());
    }
    String encoded = ScriptJson.encode(data).toString();
    String literal =
        JSONObject.quote(encoded).replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
    StringBuilder source =
        new StringBuilder("var CoreData = JSON.parse(" + literal + ");\n")
            .append(payload.getString("main.js"));
    List<String> modules = new ArrayList<>();
    payload
        .keys()
        .forEachRemaining(
            k -> {
              if (k.endsWith(".js") && !k.equals("main.js")) modules.add(k);
            });
    Collections.sort(modules);
    for (String name : modules) {
      if (!name.matches("[a-zA-Z0-9_.-]+[.]js"))
        throw new IllegalArgumentException("Invalid core module name");
      source.append("\n;\n").append(payload.getString(name));
    }
    if (source.length() > 4 * 1024 * 1024)
      throw new IllegalArgumentException("Core source too large");
    return source.toString();
  }

  private CoreSource() {}
}
