package io.github.astrbot.mobile.ui;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.*;
import io.github.astrbot.mobile.core.*;
import java.time.ZoneId;
import java.util.*;
import org.json.*;

/** Native grouped metrics; script core owns classification and server-version adaptation. */
final class StatusDashboard {
  static void render(MainActivity host, LinearLayout parent, Object data) throws Exception {
    JSONArray sections = CoreRuntime.array("status.sections", CoreRuntime.args("data", data));
    parent.removeAllViews();
    for (int i = 0; i < sections.length(); i++) {
      JSONObject section = sections.getJSONObject(i);
      JSONArray metrics = section.getJSONArray("metrics");
      LinearLayout card = host.group(parent);
      card.setPadding(host.dp(16), host.dp(12), host.dp(16), host.dp(14));
      TextView heading = host.label(section.getString("title"), 18, MainActivity.INK);
      heading.setTypeface(null, Typeface.BOLD);
      card.addView(heading);
      for (int j = 0; j < metrics.length(); j++) {
        JSONObject m = metrics.getJSONObject(j);
        JSONArray keys = m.getJSONArray("path");
        List<String> path = new ArrayList<>();
        for (int n = 0; n < keys.length(); n++) path.add(keys.getString(n));
        String title = m.optString("label");
        if (title.isEmpty())
          title = Appearance.fieldName(path, path.get(path.size() - 1).replace('_', ' '));
        Object value = m.opt("value");
        String kind = m.getString("kind");
        String formatted =
            StatusFormat.format(
                kind, value, Appearance.language().equals("zh"), ZoneId.systemDefault());
        LinearLayout line = new LinearLayout(host);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setMinimumHeight(host.dp(48));
        TextView name = host.label(title, 14, MainActivity.MUTED);
        name.setMaxLines(2);
        line.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        TextView result = host.label(formatted, 16, MainActivity.INK);
        result.setGravity(Gravity.END);
        result.setTypeface(null, Typeface.BOLD);
        result.setTextIsSelectable(true);
        line.addView(result, new LinearLayout.LayoutParams(-2, -2));
        card.addView(line);
        String hint = m.optString("hint");
        line.setTooltipText(
            String.join(".", path) + (hint.isEmpty() ? "" : "\n" + Appearance.text(hint)));
        if (kind.equals("percent")
            && value instanceof Number
            && Double.isFinite(((Number) value).doubleValue())) {
          double amount = ((Number) value).doubleValue();
          ProgressBar progress =
              new ProgressBar(host, null, android.R.attr.progressBarStyleHorizontal);
          progress.setMax(1000);
          progress.setProgress((int) Math.round(Math.max(0, Math.min(100, amount)) * 10));
          progress.setProgressTintList(
              ColorStateList.valueOf(
                  amount >= 90 ? 0xffcb6572 : amount >= 75 ? 0xffc69048 : MainActivity.ACCENT));
          progress.setProgressBackgroundTintList(
              ColorStateList.valueOf(Appearance.dark() ? 0xff3a455c : 0xffe3e9f5));
          progress.setContentDescription(Appearance.text(title) + " " + formatted);
          LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, host.dp(8));
          lp.bottomMargin = host.dp(12);
          card.addView(progress, lp);
        }
        if (!hint.isEmpty()) card.addView(host.label(hint, 11, MainActivity.MUTED));
      }
    }
  }

  private StatusDashboard() {}
}
