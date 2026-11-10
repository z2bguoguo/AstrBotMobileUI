package io.github.astrbot.mobile.ui;

import static org.junit.Assert.*;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class DeviceTest {
  private TextView find(View root, String text) {
    if (root instanceof TextView && ((TextView) root).getText().toString().equals(text))
      return (TextView) root;
    if (root instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) root;
      for (int i = 0; i < group.getChildCount(); i++) {
        TextView result = find(group.getChildAt(i), text);
        if (result != null) return result;
      }
    }
    return null;
  }

  @Test
  public void nativeHomeAndConnectionFormSurviveRecreation() {
    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
      scenario.onActivity(
          a -> {
            View root = a.getWindow().getDecorView();
            assertNotNull(find(root, "AstrBot Mobile"));
            TextView add = find(root, "＋ 添加连接");
            assertNotNull(add);
            add.performClick();
            assertNotNull(find(a.getWindow().getDecorView(), "保存连接"));
          });
      scenario.recreate();
      scenario.onActivity(a -> assertNotNull(find(a.getWindow().getDecorView(), "AstrBot Mobile")));
    }
  }

  @Test
  public void vaultRoundtripAndTamperDetection() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    String original = context.getSharedPreferences("vault", 0).getString("data", null);
    try {
      Vault v = new Vault(context);
      JSONObject expected = new JSONObject().put("password", "fixture-value");
      v.save(expected);
      assertEquals(expected.toString(), v.load().toString());
      String stored = context.getSharedPreferences("vault", 0).getString("data", "");
      assertFalse(stored.contains("fixture-value"));
      context
          .getSharedPreferences("vault", 0)
          .edit()
          .putString("data", "invalid:ciphertext")
          .commit();
      assertThrows(Exception.class, v::load);
    } finally {
      android.content.SharedPreferences.Editor editor =
          context.getSharedPreferences("vault", 0).edit();
      if (original == null) editor.remove("data");
      else editor.putString("data", original);
      editor.commit();
    }
  }

  @Test
  public void htmlNavigationStaysInsideSelectedInstance() {
    assertTrue(PluginPageActivity.allowed("https://host/bot", "https://host/bot/api/v1/plugins/a"));
    assertFalse(PluginPageActivity.allowed("https://host/bot", "https://evil/api/token"));
    assertFalse(PluginPageActivity.allowed("https://host/bot", "https://host/api/token"));
    assertFalse(PluginPageActivity.allowed("https://host/bot", "http://host/bot/api/token"));
    assertFalse(
        PluginPageActivity.allowed("https://host/bot", "https://host/bot/api/%2e%2e/admin"));
  }

  @Test
  public void screenshotsRemainAllowedForTroubleshooting() throws Exception {
    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
      scenario.onActivity(
          a ->
              assertEquals(
                  0,
                  a.getWindow().getAttributes().flags
                      & android.view.WindowManager.LayoutParams.FLAG_SECURE));
      capture("connections.png");
    }
  }

  @Test
  public void interactiveSettingsUseSwitchAndDetachedDraft() throws Exception {
    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
      scenario.onActivity(
          a -> {
            try {
              JSONObject source =
                  new JSONObject()
                      .put("enabled", true)
                      .put("system_prompt", "你是一个友好的助手。\n请清晰回答问题。")
                      .put("temperature", 0.7)
                      .put("args", new org.json.JSONArray().put("--verbose"));
              SettingsEditor editor =
                  new SettingsEditor(
                      a,
                      "配置预览",
                      source,
                      new JSONObject(),
                      new JSONObject(),
                      java.util.Set.of(),
                      () -> {},
                      (config, changed) -> fail("未点击保存不得提交"));
              editor.show();
              View root = a.getWindow().getDecorView();
              TextView enabled = find(root, "启用");
              assertTrue(enabled instanceof android.widget.Switch);
              enabled.performClick();
              assertFalse(((android.widget.Switch) enabled).isChecked());
              assertTrue(source.getBoolean("enabled"));
              assertEquals(1, editor.model.changes().size());
              assertNull(find(root, "POST"));
            } catch (Exception e) {
              throw new AssertionError(e);
            }
          });
      capture("settings.png");
    }
  }

  private void capture(String name) throws Exception {
    android.app.Instrumentation instrumentation =
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation();
    instrumentation.waitForIdleSync();
    Thread.sleep(150);
    android.graphics.Bitmap image = instrumentation.getUiAutomation().takeScreenshot();
    assertNotNull(image);
    java.io.File dir =
        new java.io.File(
            instrumentation.getTargetContext().getExternalFilesDir(null), "ui-previews");
    assertTrue(dir.isDirectory() || dir.mkdirs());
    try (java.io.FileOutputStream out = new java.io.FileOutputStream(new java.io.File(dir, name))) {
      assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out));
    } finally {
      image.recycle();
    }
  }
}
