package io.github.astrbot.mobile.ui;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.Color;
import android.widget.*;
import java.util.*;
import org.json.*;

/**
 * Local UI preferences and script-supplied bilingual copy. User-entered values are never rewritten.
 */
final class Appearance {
  @android.annotation.SuppressLint("StaticFieldLeak") // Application context only.
  private static Context context;

  private static JSONObject locales, fieldData;

  static Context localized(Context base) {
    String preference = base.getSharedPreferences("appearance", 0).getString("language", "system");
    if (preference.equals("system")) return base;
    Configuration config = new Configuration(base.getResources().getConfiguration());
    config.setLocale(Locale.forLanguageTag(preference));
    return base.createConfigurationContext(config);
  }

  static void initialize(Context ctx) {
    context = ctx.getApplicationContext();
    palette = null;
    paletteKey = null;
    try {
      locales = new JSONObject(CoreStore.data("locales.json"));
      fieldData = new JSONObject(CoreStore.data("fields.json"));
    } catch (Exception e) {
      locales = new JSONObject();
      fieldData = new JSONObject();
    }
  }

  private static SharedPreferences prefs() {
    return context.getSharedPreferences("appearance", 0);
  }

  static String language() {
    String lang = prefs().getString("language", "system");
    return lang.equals("system")
        ? (Locale.getDefault().getLanguage().equals("zh") ? "zh" : "en")
        : lang;
  }

  private static JSONObject palette;
  private static String paletteKey;

  private static JSONObject palette() {
    String mode = prefs().getString("theme", "system");
    int accent = prefs().getInt("accent", 0xff526da9);
    boolean systemDark =
        (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
            == Configuration.UI_MODE_NIGHT_YES;
    String key = mode + accent + systemDark;
    if (palette == null || !key.equals(paletteKey)) {
      palette =
          io.github.astrbot.mobile.core.CoreRuntime.object(
              "ui.theme",
              io.github.astrbot.mobile.core.CoreRuntime.args(
                  "mode", mode, "accent", accent, "systemDark", systemDark));
      paletteKey = key;
    }
    return palette;
  }

  static boolean dark() {
    return palette().optBoolean("dark");
  }

  static int accent() {
    return palette().optInt("accent");
  }

  static int ink() {
    return palette().optInt("ink");
  }

  static int muted() {
    return palette().optInt("muted");
  }

  static int surface() {
    return palette().optInt("surface");
  }

  static int onAccent() {
    return palette().optInt("onAccent");
  }

  static int[] gradient() {
    JSONArray values = palette().optJSONArray("gradient");
    int[] result = new int[values.length()];
    for (int i = 0; i < result.length; i++) result[i] = values.optInt(i);
    return result;
  }

  static int color(int original) {
    if (original == 0xff526da9) return accent();
    if (original == 0xff252d3e) return ink();
    if (original == 0xff667086) return muted();
    if (original != Color.TRANSPARENT
        && Color.red(original) > 200
        && Color.green(original) > 200
        && Color.blue(original) > 200) return surface();
    return original;
  }

  static String text(String raw) {
    if (raw == null || context == null) return raw;
    JSONObject map = locales.optJSONObject(language());
    if (map == null) return raw;
    String exact = map.optString(raw, "");
    if (!exact.isEmpty()) return exact;
    // Dynamic labels contain known UI fragments and untouched server/user values.
    if (language().equals("en")) {
      String[] prefixes = {
        "保存更改（",
        "下载更新 ",
        "发现新版本 ",
        "已下载 ",
        "已连接 · ",
        "当前版本 ",
        "全屏编辑 · ",
        "展开编辑 · ",
        "打开 ",
        "收到 ",
        "数值不能大于 ",
        "数值不能小于 ",
        "更新服务器返回 HTTP ",
        "第 ",
        "＋ 新建"
      };
      for (String prefix : prefixes)
        if (raw.startsWith(prefix))
          return map.optString(prefix, prefix) + raw.substring(prefix.length()).replace("）", ")");
      if (raw.matches("[0-9]+ 项设置")) return raw.replace(" 项设置", " settings");
      if (raw.matches("[0-9]+ 项")) return raw.replace(" 项", " items");
    }
    return raw;
  }

  static String fieldName(java.util.List<String> path, String fallback) {
    JSONObject labels = labels(), names = labels == null ? null : labels.optJSONObject("_names");
    if (names == null || path.isEmpty()) return text(fallback);
    String full =
        String.join(
            ".",
            path.stream()
                .filter(k -> !k.matches("[0-9]+"))
                .collect(java.util.stream.Collectors.toList()));
    String raw = names.optString(full, names.optString(path.get(path.size() - 1), ""));
    return raw.isBlank() ? text(fallback) : raw;
  }

  static JSONObject labels() {
    try {
      return fieldData.optJSONObject(language());
    } catch (Exception e) {
      return new JSONObject();
    }
  }

  static void explain(Activity a, String title, String details) {
    new LocalizedDialogBuilder(a)
        .setTitle(title)
        .setMessage(details)
        .setPositiveButton("知道了", null)
        .show();
  }

  static void settings(MainActivity a, LinearLayout panel) {
    NativeViews ui =
        NativeViews.template(
            a,
            "preferences",
            io.github.astrbot.mobile.core.CoreRuntime.args(
                "language",
                language(),
                "languagePreference",
                prefs().getString("language", "system"),
                "theme",
                prefs().getString("theme", "system"),
                "accent",
                String.format(Locale.ROOT, "#%06X", accent() & 0xffffff)),
            event -> {
              try {
                if (event.optString("kind").equals("choice")) {
                  JSONArray options = event.getJSONArray("options");
                  String[] labels = new String[options.length()];
                  for (int i = 0; i < labels.length; i++)
                    labels[i] = options.getJSONObject(i).getString("label");
                  new LocalizedDialogBuilder(a)
                      .setTitle(event.optString("title"))
                      .setSingleChoiceItems(
                          labels,
                          event.optInt("selected", -1),
                          (dialog, index) -> {
                            prefs()
                                .edit()
                                .putString(
                                    event.optString("key"),
                                    options.optJSONObject(index).optString("value"))
                                .apply();
                            dialog.dismiss();
                            a.recreate();
                          })
                      .show();
                } else {
                  NativeViews[] body = new NativeViews[1];
                  body[0] =
                      NativeViews.template(
                          a,
                          "color",
                          io.github.astrbot.mobile.core.CoreRuntime.args(
                              "value",
                              String.format(Locale.ROOT, "#%06X", accent() & 0xffffff),
                              "palette",
                              event.getJSONArray("palette")),
                          picked ->
                              ((EditText) body[0].refs.get("accent"))
                                  .setText(picked.optString("value")));
                  AlertDialog dialog =
                      new LocalizedDialogBuilder(a)
                          .setTitle(event.optString("title"))
                          .setView(body[0].root)
                          .setPositiveButton("应用", null)
                          .setNegativeButton("取消", null)
                          .create();
                  dialog.setOnShowListener(
                      d ->
                          dialog
                              .getButton(AlertDialog.BUTTON_POSITIVE)
                              .setOnClickListener(
                                  v -> {
                                    String value = body[0].fields().optString("accent").trim();
                                    if (!value.matches("#[0-9a-fA-F]{6}")) {
                                      ((EditText) body[0].refs.get("accent")).setError("#RRGGBB");
                                      return;
                                    }
                                    prefs()
                                        .edit()
                                        .putInt("accent", Color.parseColor(value))
                                        .apply();
                                    dialog.dismiss();
                                    a.recreate();
                                  }));
                  dialog.show();
                }
              } catch (Exception e) {
                a.error(e);
              }
            });
    ui.attach(panel, ui.root, ui.rootNode);
  }

  private Appearance() {}
}
