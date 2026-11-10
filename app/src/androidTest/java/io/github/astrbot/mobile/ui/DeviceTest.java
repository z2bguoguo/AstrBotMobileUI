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
  @org.junit.Before
  public void localTestPreferences() {
    Context context = ApplicationProvider.getApplicationContext();
    context
        .getSharedPreferences("appearance", 0)
        .edit()
        .putString("language", "zh")
        .putString("theme", "light")
        .commit();
    context.getSharedPreferences("updates", 0).edit().putBoolean("check", false).commit();
  }

  @Test
  public void localeAndThemeDoNotChangeUserDraft() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    assertEquals("保存", Appearance.text("Save"));
    context
        .getSharedPreferences("appearance", 0)
        .edit()
        .putString("language", "en")
        .putString("theme", "dark")
        .commit();
    assertEquals("Save", Appearance.text("保存"));
    assertTrue(Appearance.dark());
    assertEquals("用户的提示词 original", Appearance.text("用户的提示词 original"));
    assertFalse(Appearance.labels().optString("ai_group.name").isEmpty());
  }

  @Test
  public void updateProviderRestrictsPathsAndWriteAccess() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    UpdateProvider provider = new UpdateProvider();
    android.content.pm.ProviderInfo info = new android.content.pm.ProviderInfo();
    info.authority = context.getPackageName() + ".updates";
    provider.attachInfo(context, info);
    android.net.Uri valid = android.net.Uri.parse("content://" + info.authority + "/update.apk");
    assertEquals("application/vnd.android.package-archive", provider.getType(valid));
    assertThrows(SecurityException.class, () -> provider.openFile(valid, "w"));
    assertThrows(
        SecurityException.class,
        () -> provider.getType(android.net.Uri.parse("content://" + info.authority + "/../vault")));
    assertThrows(
        SecurityException.class,
        () -> provider.getType(android.net.Uri.parse("content://foreign/update.apk")));
    android.content.pm.ProviderInfo registered =
        context
            .getPackageManager()
            .getProviderInfo(new android.content.ComponentName(context, UpdateProvider.class), 0);
    assertFalse(registered.exported);
    assertTrue(registered.grantUriPermissions);
  }

  @Test
  public void navigationRowsRespondAcrossTextIconWhitespaceAndArrow() throws Exception {
    java.util.concurrent.atomic.AtomicInteger clicks =
        new java.util.concurrent.atomic.AtomicInteger();
    java.util.concurrent.atomic.AtomicReference<android.widget.LinearLayout> rowRef =
        new java.util.concurrent.atomic.AtomicReference<>();
    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
      scenario.onActivity(
          a -> {
            a.screen("Touch fixture", "", null);
            android.widget.LinearLayout parent = a.card("", "");
            a.navRow(
                parent, "语言 / Language", "Connection and appearance", "◇", clicks::incrementAndGet);
            rowRef.set((android.widget.LinearLayout) parent.getChildAt(0));
          });
      androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync();
      scenario.onActivity(
          a -> {
            android.widget.LinearLayout row = rowRef.get();
            assertTrue(row.getWidth() > 0);
            assertTrue(row.getHeight() > 0);
            android.view.View icon = row.getChildAt(0),
                text = row.getChildAt(1),
                arrow = row.getChildAt(2);
            android.view.ViewGroup labels = (android.view.ViewGroup) text;
            java.util.List<android.view.View> targets =
                java.util.List.of(icon, labels.getChildAt(0), labels.getChildAt(1), arrow);
            for (android.view.View target : targets) {
              int[] xy = new int[2];
              target.getLocationInWindow(xy);
              touch(a, xy[0] + target.getWidth() / 2f, xy[1] + target.getHeight() / 2f);
            }
            int[] xy = new int[2];
            row.getLocationInWindow(xy);
            touch(a, xy[0] + row.getWidth() / 2f, xy[1] + 2);
            touch(a, xy[0] + 2, xy[1] + row.getHeight() / 2f);
            assertTrue(row.isFocusable());
          });
      androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync();
      scenario.onActivity(a -> assertEquals(6, clicks.get()));
    }
  }

  private static void touch(MainActivity activity, float x, float y) {
    long time = android.os.SystemClock.uptimeMillis();
    android.view.MotionEvent down =
        android.view.MotionEvent.obtain(time, time, android.view.MotionEvent.ACTION_DOWN, x, y, 0);
    android.view.MotionEvent up =
        android.view.MotionEvent.obtain(
            time, time + 20, android.view.MotionEvent.ACTION_UP, x, y, 0);
    try {
      activity.dispatchTouchEvent(down);
      activity.dispatchTouchEvent(up);
    } finally {
      down.recycle();
      up.recycle();
    }
  }

  @Test
  public void dashboardShowsLocalizedUnitsAndNativeProgress() throws Exception {
    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
      scenario.onActivity(
          a -> {
            try {
              a.screen("运行概览", "", null);
              JSONObject data =
                  new JSONObject(
                      "{\"running\":{\"hours\":0,\"minutes\":10,\"seconds\":52},\"memory\":{\"process\":660,\"system\":1699},\"cpu_percent\":3.0,\"thread_count\":22,\"start_time\":1794155493}");
              StatusDashboard.render(a, a.content, data);
              View root = a.getWindow().getDecorView();
              assertNotNull(find(root, "CPU 使用率"));
              assertNotNull(find(root, "3.0%"));
              assertNotNull(find(root, "660 MiB"));
              assertNotNull(find(root, "1.66 GiB"));
              assertNotNull(find(root, "10 分 52 秒"));
              assertNull(find(root, "seconds"));
              assertEquals(2, progressCount(root));
              assertEquals(
                  "消息平台设置",
                  Appearance.fieldName(
                      java.util.List.of("platform_settings"), "platform settings"));
              assertEquals(
                  "配置格式版本",
                  Appearance.fieldName(java.util.List.of("config_version"), "config version"));
              assertEquals(
                  "模型服务来源",
                  Appearance.fieldName(java.util.List.of("provider_sources"), "provider sources"));
            } catch (Exception e) {
              throw new AssertionError(e);
            }
          });
      capture("dashboard.png");
    }
  }

  @Test
  public void legacyRootConfigurationNamesAreChineseWithoutMetadata() throws Exception {
    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
      scenario.onActivity(
          a -> {
            try {
              new SettingsEditor(
                      a,
                      "通用设置",
                      new JSONObject(
                          "{\"config_version\":2,\"platform_settings\":{\"enable\":true},\"provider_sources\":[]}"),
                      new JSONObject(),
                      Appearance.labels(),
                      java.util.Set.of(),
                      () -> {},
                      (config, changed) -> fail("Viewing labels must not save"))
                  .show();
              View root = a.getWindow().getDecorView();
              assertNotNull(find(root, "配置格式版本"));
              assertNotNull(find(root, "消息平台设置"));
              assertNotNull(find(root, "模型服务来源"));
              assertNull(find(root, "config version"));
              assertNull(find(root, "platform settings"));
              assertNull(find(root, "provider sources"));
            } catch (Exception e) {
              throw new AssertionError(e);
            }
          });
      capture("configuration-groups.png");
    }
  }

  private int progressCount(View view) {
    if (view instanceof android.widget.ProgressBar) return 1;
    int total = 0;
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) total += progressCount(group.getChildAt(i));
    }
    return total;
  }

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
    // UTP uninstalls the app after testing; copy fixture previews before its external files
    // disappear.
    shell(instrumentation, "mkdir -p /sdcard/Download/astrbot-ui-previews");
    shell(
        instrumentation,
        "cp "
            + new java.io.File(dir, name).getAbsolutePath()
            + " /sdcard/Download/astrbot-ui-previews/"
            + name);
    String retained = shell(instrumentation, "ls /sdcard/Download/astrbot-ui-previews/" + name);
    assertTrue("Failed to retain the test UI preview", retained.contains(name));
  }

  private String shell(android.app.Instrumentation instrumentation, String command)
      throws Exception {
    try (android.os.ParcelFileDescriptor output =
            instrumentation.getUiAutomation().executeShellCommand(command);
        java.io.InputStream input =
            new android.os.ParcelFileDescriptor.AutoCloseInputStream(output)) {
      return new String(
          io.github.astrbot.mobile.core.HttpTransport.readLimited(input, 4096),
          java.nio.charset.StandardCharsets.UTF_8);
    }
  }
}
