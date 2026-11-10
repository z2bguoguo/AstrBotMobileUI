package io.github.astrbot.mobile.core;

import org.json.*;

public final class Schema {
  private Schema() {}

  public static JSONObject resolve(JSONObject schema, JSONObject spec) {
    return CoreRuntime.object("schema.resolve", CoreRuntime.args("schema", schema, "spec", spec));
  }

  public static Object sample(JSONObject schema, JSONObject spec, int depth) {
    return CoreRuntime.call(
        "schema.sample", CoreRuntime.args("schema", schema, "spec", spec, "depth", depth));
  }

  public static JSONObject requestSchema(Endpoint e, JSONObject spec) {
    return CoreRuntime.object(
        "schema.request", CoreRuntime.args("definition", e.definition, "spec", spec));
  }

  public static String mediaType(Endpoint e, JSONObject spec) {
    return CoreRuntime.text(
        "schema.media", CoreRuntime.args("definition", e.definition, "spec", spec));
  }
}
