package io.github.astrbot.mobile.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.*;
import org.mozilla.javascript.*;

/** JSON-only interpreted script boundary. No Java objects or host APIs enter the script. */
public final class CoreRuntime {
  private static volatile String source;
  private static volatile long revision;
  private static final ThreadLocal<Engine> ENGINES = new ThreadLocal<>();
  private static final Factory FACTORY = new Factory();

  private record Engine(long revision, Scriptable scope, Function dispatch) {}

  private static final class Factory extends ContextFactory {
    private final ThreadLocal<Long> deadline = new ThreadLocal<>();

    @Override
    protected Context makeContext() {
      Context c = super.makeContext();
      c.setOptimizationLevel(-1);
      c.setLanguageVersion(Context.VERSION_ES6);
      c.setClassShutter(name -> false);
      c.setInstructionObserverThreshold(10000);
      return c;
    }

    @Override
    protected void observeInstructionCount(Context cx, int count) {
      if (System.nanoTime() > deadline.get())
        throw new IllegalStateException("Script execution timed out");
    }
  }

  public static void validate(String script) {
    // Validate before changing the active runtime; malformed downloads never replace the old core.
    FACTORY.call(
        cx -> {
          FACTORY.deadline.set(System.nanoTime() + 3_000_000_000L);
          Scriptable s = cx.initSafeStandardObjects();
          cx.evaluateString(s, script, "core", 1, null);
          Object fn = s.get("dispatch", s);
          if (!(fn instanceof Function))
            throw new IllegalArgumentException("Missing script dispatch");
          String ping =
              Context.toString(((Function) fn).call(cx, s, s, new Object[] {"ping", "{}"}));
          if (!ping.contains("\"abi\":1"))
            throw new IllegalArgumentException("Incompatible script ABI");
          return null;
        });
  }

  public static synchronized void configure(String script) {
    validate(script);
    source = script;
    revision++;
  }

  public static JSONObject args(Object... fields) {
    try {
      JSONObject o = new JSONObject();
      for (int i = 0; i < fields.length; i += 2) o.put((String) fields[i], fields[i + 1]);
      return o;
    } catch (JSONException e) {
      throw new IllegalArgumentException(e);
    }
  }

  public static Object call(String name, JSONObject args) {
    if (source == null) {
      try (InputStream in =
          CoreRuntime.class.getClassLoader().getResourceAsStream("core/main.js")) {
        if (in == null) throw new IOException("Script core not initialized");
        configure(
            new String(HttpTransport.readLimited(in, 8 * 1024 * 1024), StandardCharsets.UTF_8));
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }
    try {
      String encoded = ScriptJson.encode(args).toString();
      String result =
          FACTORY.call(
              cx -> {
                FACTORY.deadline.set(System.nanoTime() + 3_000_000_000L);
                Engine engine = ENGINES.get();
                if (engine == null || engine.revision() != revision) {
                  Scriptable scope = cx.initSafeStandardObjects();
                  cx.evaluateString(scope, source, "core", 1, null);
                  engine = new Engine(revision, scope, (Function) scope.get("dispatch", scope));
                  ENGINES.set(engine);
                }
                return Context.toString(
                    engine
                        .dispatch()
                        .call(cx, engine.scope(), engine.scope(), new Object[] {name, encoded}));
              });
      return ScriptJson.decode(new JSONTokener(result).nextValue());
    } catch (RhinoException | JSONException e) {
      throw new IllegalArgumentException(e.getMessage(), e);
    }
  }

  public static JSONObject object(String name, JSONObject args) {
    return (JSONObject) call(name, args);
  }

  public static JSONArray array(String name, JSONObject args) {
    return (JSONArray) call(name, args);
  }

  public static String text(String name, JSONObject args) {
    return String.valueOf(call(name, args));
  }

  private CoreRuntime() {}
}
