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

  private static JSONObject locales;

  static Context localized(Context base) {
    String preference = base.getSharedPreferences("appearance", 0).getString("language", "system");
    if (preference.equals("system")) return base;
    Configuration config = new Configuration(base.getResources().getConfiguration());
    config.setLocale(Locale.forLanguageTag(preference));
    return base.createConfigurationContext(config);
  }

  static void initialize(Context ctx) {
    context = ctx.getApplicationContext();
    try {
      locales = new JSONObject(CoreStore.data("locales.json"));
    } catch (Exception e) {
      locales = new JSONObject();
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

  static boolean dark() {
    String mode = prefs().getString("theme", "system");
    return mode.equals("dark")
        || (mode.equals("system")
            && (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES);
  }

  static int accent() {
    return prefs().getInt("accent", 0xff526da9);
  }

  static int ink() {
    return dark() ? 0xffedf1fa : 0xff252d3e;
  }

  static int muted() {
    return dark() ? 0xffb4bfd2 : 0xff667086;
  }

  static int surface() {
    return dark() ? 0xff232b3b : 0xeeffffff;
  }

  static int onAccent() {
    int c = accent();
    return (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000 > 170
        ? 0xff172032
        : Color.WHITE;
  }

  static int[] gradient() {
    return dark()
        ? new int[] {0xff111827, 0xff182132, 0xff201e31}
        : new int[] {mix(accent(), Color.WHITE, .86f), 0xfff1f5fd, 0xffeeeaf6};
  }

  private static int mix(int a, int b, float ratio) {
    return Color.rgb(
        (int) (Color.red(a) * (1 - ratio) + Color.red(b) * ratio),
        (int) (Color.green(a) * (1 - ratio) + Color.green(b) * ratio),
        (int) (Color.blue(a) * (1 - ratio) + Color.blue(b) * ratio));
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

  static JSONObject labels() {
    try {
      return new JSONObject(CoreStore.data("fields.json")).optJSONObject(language());
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
    a.navRow(
        panel,
        "Language / 语言",
        language().equals("zh") ? "简体中文" : "English",
        "A",
        () ->
            new LocalizedDialogBuilder(a)
                .setTitle("Language / 语言")
                .setSingleChoiceItems(
                    new String[] {"跟随系统 / System", "简体中文", "English"},
                    Arrays.asList("system", "zh", "en")
                        .indexOf(prefs().getString("language", "system")),
                    (d, i) -> {
                      prefs()
                          .edit()
                          .putString("language", new String[] {"system", "zh", "en"}[i])
                          .apply();
                      d.dismiss();
                      a.recreate();
                    })
                .show());
    a.navRow(
        panel,
        "Theme / 主题",
        prefs().getString("theme", "system"),
        "◐",
        () ->
            new LocalizedDialogBuilder(a)
                .setTitle("Theme / 主题")
                .setSingleChoiceItems(
                    new String[] {"跟随系统 / System", "浅色 / Light", "深色 / Dark"},
                    Arrays.asList("system", "light", "dark")
                        .indexOf(prefs().getString("theme", "system")),
                    (d, i) -> {
                      prefs()
                          .edit()
                          .putString("theme", new String[] {"system", "light", "dark"}[i])
                          .apply();
                      d.dismiss();
                      a.recreate();
                    })
                .show());
    a.navRow(
        panel,
        "Accent / 主题色",
        String.format(Locale.ROOT, "#%06X", accent() & 0xffffff),
        "●",
        () -> {
          LinearLayout box = a.vertical();
          box.setPadding(a.dp(20), a.dp(8), a.dp(20), a.dp(8));
          EditText input =
              a.input(
                  box,
                  "#RRGGBB",
                  String.format(Locale.ROOT, "#%06X", accent() & 0xffffff),
                  false,
                  false);
          LinearLayout swatches = new LinearLayout(a);
          box.addView(swatches);
          for (int color : new int[] {0xff526da9, 0xff8670b7, 0xff3b8574, 0xffb76683, 0xffc17b3f}) {
            TextView swatch = a.label("●", 28, color);
            swatch.setPadding(a.dp(10), 0, a.dp(10), 0);
            swatches.addView(swatch);
            swatch.setOnClickListener(
                v -> input.setText(String.format(Locale.ROOT, "#%06X", color & 0xffffff)));
          }
          AlertDialog dialog =
              new LocalizedDialogBuilder(a)
                  .setTitle("Accent / 主题色")
                  .setView(box)
                  .setPositiveButton("应用", null)
                  .setNegativeButton("取消", null)
                  .create();
          dialog.setOnShowListener(
              d ->
                  dialog
                      .getButton(AlertDialog.BUTTON_POSITIVE)
                      .setOnClickListener(
                          v -> {
                            String value = input.getText().toString().trim();
                            if (!value.matches("#[0-9a-fA-F]{6}")) {
                              input.setError("#RRGGBB");
                              return;
                            }
                            prefs().edit().putInt("accent", Color.parseColor(value)).apply();
                            dialog.dismiss();
                            a.recreate();
                          }));
          dialog.show();
        });
  }

  private Appearance() {}
}
