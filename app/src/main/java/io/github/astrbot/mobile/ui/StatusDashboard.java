package io.github.astrbot.mobile.ui;

import android.widget.LinearLayout;
import io.github.astrbot.mobile.core.*;
import org.json.*;

/** Compatibility entry point. The entire dashboard tree and progress policy live in ui.js. */
final class StatusDashboard {
  static void render(MainActivity host, LinearLayout parent, Object data) throws Exception {
    JSONObject labels = Appearance.labels();
    NativeViews ui =
        NativeViews.template(
            host,
            "status",
            CoreRuntime.args("data", data, "names", labels.optJSONObject("_names")),
            e -> {});
    parent.removeAllViews();
    ui.attach(parent, ui.root, ui.rootNode);
  }

  private StatusDashboard() {}
}
