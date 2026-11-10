package io.github.astrbot.mobile.core;

import static org.junit.Assert.*;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.json.*;
import org.junit.Test;

public class CoreRuntimeTest {
  private String bundled() throws Exception {
    try (InputStream in = getClass().getClassLoader().getResourceAsStream("core/main.js")) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  public void scriptsCannotAccessJavaPackages() throws Exception {
    String good = bundled();
    try {
      CoreRuntime.configure(
          "function dispatch(n,j){if(n==='ping')return '{\"abi\":1}'; return JSON.stringify({java:typeof java,packages:typeof Packages});}");
      JSONObject result = CoreRuntime.object("inspect", new JSONObject());
      assertEquals("undefined", result.getString("java"));
      assertEquals("undefined", result.getString("packages"));
    } finally {
      CoreRuntime.configure(good);
    }
  }

  @Test
  public void invalidCoreCannotReplaceWorkingCore() throws Exception {
    CoreRuntime.configure(bundled());
    assertThrows(Exception.class, () -> CoreRuntime.configure("invalid {{"));
    assertThrows(Exception.class, () -> CoreRuntime.configure("function dispatch(){return '{}';}"));
    assertEquals(1, CoreRuntime.object("ping", new JSONObject()).getInt("abi"));
  }

  @Test
  public void exactNumbersAndUnknownFieldsSurviveTheBoundary() throws Exception {
    JSONObject config =
        new JSONObject()
            .put("huge", new BigDecimal("900719925474099312345"))
            .put("precise", new BigDecimal("0.12345678901234567890"));
    JSONObject plan =
        CoreRuntime.object(
            "management.plan",
            CoreRuntime.args(
                "op", "saveConfiguration", "id", "system", "modern", true, "config", config));
    assertEquals(config.get("huge").toString(), plan.getJSONObject("body").get("huge").toString());
    assertEquals(
        config.get("precise").toString(), plan.getJSONObject("body").get("precise").toString());
  }

  @Test
  public void recursiveMetadataAndApiGenerationUseBundledScript() throws Exception {
    CoreRuntime.configure(bundled());
    assertEquals(1, CoreRuntime.object("ping", new JSONObject()).getInt("abi"));
    assertEquals(
        "/api/v1/stats",
        CoreRuntime.object("management.plan", CoreRuntime.args("op", "overview", "modern", true))
            .getString("path"));
  }
}
