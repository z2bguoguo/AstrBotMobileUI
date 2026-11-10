package io.github.astrbot.mobile.core;

import static org.junit.Assert.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.*;
import org.junit.Test;

public class CoreSourceTest {
  private String resource(String name) throws Exception {
    try (InputStream in = getClass().getClassLoader().getResourceAsStream("core/" + name)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  public void signedModulesAndDataLoadWithNoNativePageClass() throws Exception {
    JSONObject payload =
        new JSONObject()
            .put("main.js", resource("main.js"))
            .put("ui.js", resource("ui.js"))
            .put("help.js", resource("help.js"))
            .put("help.json", resource("help.json"))
            .put("locales.json", resource("locales.json"))
            .put("fields.json", resource("fields.json"));
    CoreRuntime.configure(CoreSource.script(payload));
    JSONObject node =
        CoreRuntime.object(
            "ui.nav", CoreRuntime.args("data", CoreRuntime.args("title", "test", "active", true)));
    assertEquals("row", node.getString("type"));
    assertTrue(node.getBoolean("atomic"));
    JSONObject presentation =
        CoreRuntime.object(
            "ui.field",
            CoreRuntime.args(
                "data",
                CoreRuntime.args(
                    "path", new JSONArray().put("platform_settings"), "meta", new JSONObject()),
                "language",
                "zh"));
    assertEquals("消息平台设置", presentation.getString("title"));
    JSONObject meta =
        CoreRuntime.object(
            "form.metadata",
            CoreRuntime.args(
                "root", new JSONObject(), "path", new JSONArray().put("config_version")));
    JSONObject labels = new JSONObject(resource("fields.json")).getJSONObject("zh");
    assertTrue(labels.getString(meta.getString("hint")).contains("填写示例"));
    assertTrue(labels.getString(meta.getString("hint")).contains("不要手动"));
  }

  @Test
  public void additionalSignedModuleCanAddScreenWithoutChangingApk() throws Exception {
    JSONObject payload =
        new JSONObject()
            .put("main.js", resource("main.js"))
            .put("ui.js", resource("ui.js"))
            .put(
                "z-extra.js",
                "CoreModules['ui.extra']=function(a){return {type:'text',text:'downloaded"
                    + " screen'};};");
    CoreRuntime.configure(CoreSource.script(payload));
    assertEquals(
        "downloaded screen", CoreRuntime.object("ui.extra", new JSONObject()).getString("text"));
  }

  @Test
  public void moduleNamesCannotRequestFilesystemPaths() throws Exception {
    JSONObject payload =
        new JSONObject().put("main.js", resource("main.js")).put("../outside.js", "bad");
    assertThrows(IllegalArgumentException.class, () -> CoreSource.script(payload));
  }
}
