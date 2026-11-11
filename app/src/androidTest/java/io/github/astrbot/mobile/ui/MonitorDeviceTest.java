package io.github.astrbot.mobile.ui;

import static org.junit.Assert.*;

import android.content.Context;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import io.github.astrbot.mobile.core.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MonitorDeviceTest {
  @Before
  public void preferences() {
    Context c = ApplicationProvider.getApplicationContext();
    c.getSharedPreferences("updates", 0).edit().putBoolean("check", false).commit();
    c.getSharedPreferences("appearance", 0)
        .edit()
        .putString("language", "zh")
        .putString("theme", "light")
        .commit();
  }

  private static void set(Object target, String key, Object value) throws Exception {
    java.lang.reflect.Field field = MainActivity.class.getDeclaredField(key);
    field.setAccessible(true);
    field.set(target, value);
  }

  private static Object get(Object target, String key) throws Exception {
    java.lang.reflect.Field field = MainActivity.class.getDeclaredField(key);
    field.setAccessible(true);
    return field.get(target);
  }

  private static void invoke(MainActivity a, String name, Class<?>[] types, Object... args)
      throws Exception {
    java.lang.reflect.Method m = MainActivity.class.getDeclaredMethod(name, types);
    m.setAccessible(true);
    m.invoke(a, args);
  }

  private static void setup(MainActivity a, Transport transport) throws Exception {
    Connection c = new Connection();
    c.id = "monitor-fixture";
    c.name = "测试实例";
    c.url = "https://fixture.test";
    ApiClient client = new ApiClient(c, transport);
    client.modern = true;
    set(a, "selected", c);
    set(a, "client", client);
    set(a, "state", new JSONObject("{\"connections\":[],\"presets\":[],\"extensions\":[]}"));
  }

  private static void open(MainActivity a, String page) throws Exception {
    invoke(
        a,
        "scriptCommand",
        new Class<?>[] {JSONObject.class},
        CoreRuntime.args("kind", "event", "handler", "monitor.open", "page", page));
  }

  private static Transport.Reply json(String data) {
    return new Transport.Reply(
        200,
        ("{\"status\":\"ok\",\"data\":" + data + "}").getBytes(StandardCharsets.UTF_8),
        "application/json");
  }

  private static TextView find(View root, String value) {
    if (root instanceof TextView && ((TextView) root).getText().toString().equals(value))
      return (TextView) root;
    if (root instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) root;
      for (int i = 0; i < group.getChildCount(); i++) {
        TextView found = find(group.getChildAt(i), value);
        if (found != null) return found;
      }
    }
    return null;
  }

  private static void waitText(ActivityScenario<MainActivity> scenario, String value)
      throws Exception {
    long end = System.currentTimeMillis() + 5000;
    AtomicBoolean found = new AtomicBoolean();
    while (System.currentTimeMillis() < end) {
      scenario.onActivity(a -> found.set(find(a.getWindow().getDecorView(), value) != null));
      if (found.get()) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        return;
      }
      Thread.sleep(50);
    }
    fail("Missing text: " + value);
  }

  private static void click(MainActivity a, View view) {
    assertNotNull(view);
    view.requestRectangleOnScreen(
        new android.graphics.Rect(0, 0, view.getWidth(), view.getHeight()), true);
    int[] pos = new int[2];
    view.getLocationInWindow(pos);
    long time = android.os.SystemClock.uptimeMillis();
    float x = pos[0] + view.getWidth() / 2f, y = pos[1] + view.getHeight() / 2f;
    android.view.MotionEvent down = android.view.MotionEvent.obtain(time, time, 0, x, y, 0),
        up = android.view.MotionEvent.obtain(time, time + 30, 1, x, y, 0);
    try {
      a.dispatchTouchEvent(down);
      a.dispatchTouchEvent(up);
    } finally {
      down.recycle();
      up.recycle();
    }
  }

  @Test
  public void statusPollingStopsInBackgroundAndOnNavigation() throws Exception {
    AtomicInteger requests = new AtomicInteger();
    CountDownLatch first = new CountDownLatch(1), second = new CountDownLatch(1);
    Transport fixture =
        (url, method, headers, body) -> {
          assertEquals("GET", method);
          int count = requests.incrementAndGet();
          if (count == 1) first.countDown();
          else second.countDown();
          return json("{\"cpu_percent\":25,\"thread_count\":2}");
        };
    AtomicReference<MainActivity> activity = new AtomicReference<>();
    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
      scenario.onActivity(
          a -> {
            try {
              activity.set(a);
              setup(a, fixture);
              invoke(a, "overview", new Class<?>[] {});
            } catch (Exception e) {
              throw new AssertionError(e);
            }
          });
      assertTrue(first.await(5, TimeUnit.SECONDS));
      waitText(scenario, "25.0%");
      scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);
      InstrumentationRegistry.getInstrumentation()
          .runOnMainSync(
              () -> {
                try {
                  assertTrue(((Map<?, ?>) get(activity.get(), "watches")).isEmpty());
                } catch (Exception e) {
                  throw new AssertionError(e);
                }
              });
      scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);
      assertTrue(second.await(5, TimeUnit.SECONDS));
      waitText(scenario, "25.0%");
      scenario.onActivity(
          a -> {
            try {
              NativeViews old = (NativeViews) get(a, "scriptView");
              java.lang.reflect.Field callback = NativeViews.class.getDeclaredField("events");
              callback.setAccessible(true);
              @SuppressWarnings("unchecked")
              java.util.function.Consumer<JSONObject> stale =
                  (java.util.function.Consumer<JSONObject>) callback.get(old);
              a.screen("离开监控", "", null);
              stale.accept(
                  CoreRuntime.args("kind", "event", "handler", "monitor.open", "page", "logs"));
              assertTrue(((Map<?, ?>) get(a, "watches")).isEmpty());
              assertTrue(((Map<?, ?>) get(a, "watchPlans")).isEmpty());
            } catch (Exception e) {
              throw new AssertionError(e);
            }
          });
    }
  }

  @Test
  public void liveLogsRenderLiteralTextAndPauseClosesTheStream() throws Exception {
    CountDownLatch closed = new CountDownLatch(1);
    PipedInputStream input = new PipedInputStream();
    PipedOutputStream output = new PipedOutputStream(input);
    StreamTransport fixture =
        new StreamTransport() {
          public Reply send(String u, String m, Map<String, String> h, byte[] b) {
            return json("{\"logs\":[{\"time\":1,\"data\":\"历史内容\",\"level\":\"INFO\"}]}");
          }

          public Stream openStream(String u, Map<String, String> h) throws IOException {
            output.write(
                "id: 2\ndata: {\"type\":\"log\",\"time\":2,\"data\":\"stream 中文\",\"level\":\"ERROR\"}\n\n"
                    .getBytes(StandardCharsets.UTF_8));
            output.flush();
            return new Stream(
                200,
                "text/event-stream",
                input,
                () -> {
                  closed.countDown();
                  try {
                    output.close();
                    input.close();
                  } catch (IOException ignored) {
                  }
                });
          }
        };
    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
      scenario.onActivity(
          a -> {
            try {
              setup(a, fixture);
              open(a, "logs");
            } catch (Exception e) {
              throw new AssertionError(e);
            }
          });
      waitText(scenario, "stream 中文");
      scenario.onActivity(a -> click(a, find(a.getWindow().getDecorView(), "暂停接收")));
      assertTrue(closed.await(5, TimeUnit.SECONDS));
      waitText(scenario, "继续接收");
      scenario.onActivity(
          a -> {
            View root = a.getWindow().getDecorView();
            assertNotNull(find(root, "历史内容"));
            assertNotNull(find(root, "stream 中文"));
          });
    } finally {
      output.close();
      input.close();
    }
  }

  @Test
  public void marketplaceRequiresConfirmationBeforeTheOnlyInstallRequest() throws Exception {
    AtomicInteger installs = new AtomicInteger();
    CountDownLatch installed = new CountDownLatch(1);
    AtomicReference<Throwable> validation = new AtomicReference<>();
    Transport fixture =
        (url, method, headers, body) -> {
          assertTrue(url.startsWith("https://fixture.test/api/"));
          if (method.equals("POST")) {
            try {
              assertTrue(url.endsWith("/plugins/install/github"));
              JSONObject value = new JSONObject(new String(body, StandardCharsets.UTF_8));
              assertEquals("https://github.com/example/tool", value.getString("repository"));
              assertFalse(value.getBoolean("ignore_version_check"));
              installs.incrementAndGet();
            } catch (Throwable failure) {
              validation.set(failure);
            } finally {
              installed.countDown();
            }
            return json("{}");
          }
          if (url.endsWith("/market"))
            return json(
                "{\"example/tool\":{\"name\":\"tool\",\"display_name\":\"工具包\",\"desc\":\"原生市场测试\",\"repo\":\"https://github.com/example/tool\",\"stars\":12}}");
          return json("{\"plugins\":[]}");
        };
    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
      scenario.onActivity(
          a -> {
            try {
              setup(a, fixture);
              open(a, "market");
            } catch (Exception e) {
              throw new AssertionError(e);
            }
          });
      waitText(scenario, "工具包");
      scenario.onActivity(a -> click(a, find(a.getWindow().getDecorView(), "工具包")));
      waitText(scenario, "安装到当前服务器");
      scenario.onActivity(a -> click(a, find(a.getWindow().getDecorView(), "安装到当前服务器")));
      InstrumentationRegistry.getInstrumentation().waitForIdleSync();
      assertEquals(0, installs.get());
      android.view.accessibility.AccessibilityNodeInfo root =
          InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();
      assertNotNull(root);
      java.util.List<android.view.accessibility.AccessibilityNodeInfo> buttons =
          root.findAccessibilityNodeInfosByText("确认");
      assertFalse(buttons.isEmpty());
      boolean clicked = false;
      for (android.view.accessibility.AccessibilityNodeInfo button : buttons)
        if (button.isClickable() && "确认".contentEquals(button.getText())) {
          clicked =
              button.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);
          break;
        }
      assertTrue(clicked);
      assertTrue("Confirmed install did not reach transport", installed.await(5, TimeUnit.SECONDS));
      assertNull("Invalid installation request: " + validation.get(), validation.get());
      assertEquals(1, installs.get());
    }
  }
}
