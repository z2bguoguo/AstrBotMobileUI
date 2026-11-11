package io.github.astrbot.mobile.ui;

import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import io.github.astrbot.mobile.core.*;
import java.util.*;
import java.util.function.Consumer;
import org.json.*;

/** Versioned native widget bridge. Layout, labels, actions and touch policy are script data. */
final class NativeViews {
  private final MainActivity host;
  private final Consumer<JSONObject> events;
  final Map<String, View> refs = new HashMap<>();
  private int nodes;
  private final Map<String, Object> choiceValues = new HashMap<>();

  NativeViews(MainActivity host, Consumer<JSONObject> events) {
    this.host = host;
    this.events = events;
  }

  static NativeViews template(
      MainActivity host, String name, JSONObject args, Consumer<JSONObject> events) {
    args =
        CoreRuntime.args(
            "data", args, "language", Appearance.language(), "dark", Appearance.dark());
    NativeViews renderer = new NativeViews(host, events);
    renderer.build(CoreRuntime.object("ui." + name, args), 0);
    return renderer;
  }

  View root;
  JSONObject rootNode;

  View build(JSONObject node, int depth) {
    if (depth > 24 || ++nodes > 2500) throw new IllegalArgumentException("UI tree exceeds limits");
    String type = node.optString("type", "column");
    View view;
    switch (type) {
      case "column":
      case "row":
        LinearLayout l = new LinearLayout(host);
        l.setOrientation(type.equals("row") ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        view = l;
        break;
      case "scroll":
        ScrollView scroll = new ScrollView(host);
        scroll.setFillViewport(true);
        view = scroll;
        break;
      case "button":
        Button button = new Button(host);
        button.setAllCaps(false);
        button.setMinHeight(0);
        button.setMinWidth(0);
        view = button;
        break;
      case "input":
        AutoCompleteTextView input = new AutoCompleteTextView(host);
        input.setThreshold(1);
        input.setText(node.optString("value"));
        input.setTextColor(Appearance.ink());
        input.setHintTextColor(Appearance.muted());
        input.setInputType(
            node.optBoolean("secret")
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                : node.optBoolean("multiline")
                    ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    : InputType.TYPE_CLASS_TEXT);
        if (node.optBoolean("multiline")) {
          input.setGravity(Gravity.TOP);
          input.setMinLines(node.optInt("minLines", 3));
          input.setMaxLines(node.optInt("maxLines", 9));
        }
        view = input;
        break;
      case "choice":
        Spinner picker = new Spinner(host);
        JSONArray options = node.optJSONArray("options");
        List<String> captions = new ArrayList<>();
        if (options != null)
          for (int i = 0; i < options.length(); i++)
            captions.add(
                Appearance.text(
                    options.optJSONObject(i) == null
                        ? options.optString(i)
                        : options.optJSONObject(i).optString("label")));
        picker.setAdapter(
            new ArrayAdapter<>(host, android.R.layout.simple_spinner_dropdown_item, captions));
        picker.setSelection(node.optInt("selected", 0));
        if (options != null && options.length() > 0)
          choiceValues.put(node.optString("id"), choice(options, node.optInt("selected", 0)));
        view = picker;
        break;
      case "slider":
        SeekBar seek = new SeekBar(host);
        seek.setMax(node.optInt("max", 100));
        seek.setMin(node.optInt("min", 0));
        seek.setProgress(node.optInt("value", 0));
        view = seek;
        break;
      case "checkbox":
        CheckBox check = new CheckBox(host);
        check.setChecked(node.optBoolean("checked"));
        view = check;
        break;
      case "switch":
        Switch toggle = new Switch(host);
        toggle.setChecked(node.optBoolean("checked"));
        view = toggle;
        break;
      case "progress":
        ProgressBar p = new ProgressBar(host, null, android.R.attr.progressBarStyleHorizontal);
        p.setMax(node.optInt("max", 1000));
        p.setProgress(node.optInt("value"));
        p.setProgressTintList(
            ColorStateList.valueOf(color(node.opt("color"), MainActivity.ACCENT)));
        p.setProgressBackgroundTintList(
            ColorStateList.valueOf(Appearance.dark() ? 0xff3a455c : 0xffe3e9f5));
        view = p;
        break;
      case "text":
        view = new TextView(host);
        break;
      case "space":
        view = new View(host);
        break;
      default:
        throw new IllegalArgumentException("Unsupported native widget: " + type);
    }
    if (root == null) {
      root = view;
      rootNode = node;
    }
    if (node.has("id")) refs.put(node.optString("id"), view);
    if (view instanceof TextView) {
      TextView text = (TextView) view;
      if (!type.equals("input")) {
        JSONObject format = node.optJSONObject("format");
        text.setText(
            format == null
                ? (node.optBoolean("literal")
                    ? node.optString("text")
                    : Appearance.text(node.optString("text")))
                : StatusFormat.format(
                    format.optString("kind"),
                    format.opt("value"),
                    Appearance.language().equals("zh"),
                    java.time.ZoneId.systemDefault(),
                    format.optString("pattern")));
      }
      text.setTextSize((float) node.optDouble("size", 14));
      text.setTextColor(color(node.opt("color"), MainActivity.INK));
      if (node.optBoolean("bold")) text.setTypeface(null, Typeface.BOLD);
      if (node.optBoolean("monospace")) text.setTypeface(Typeface.MONOSPACE);
      if (node.has("maxLines")) text.setMaxLines(node.optInt("maxLines"));
      if (node.optBoolean("selectable")) text.setTextIsSelectable(true);
      text.setGravity(gravity(node.optString("gravity")));
    } else if (view instanceof LinearLayout)
      ((LinearLayout) view).setGravity(gravity(node.optString("gravity")));
    JSONArray padding = node.optJSONArray("padding");
    if (padding != null && padding.length() == 4)
      view.setPadding(
          dp(padding.optInt(0)),
          dp(padding.optInt(1)),
          dp(padding.optInt(2)),
          dp(padding.optInt(3)));
    if (node.has("visible"))
      view.setVisibility(node.optBoolean("visible") ? View.VISIBLE : View.GONE);
    view.setMinimumHeight(dp(node.optInt("minHeight")));
    view.setEnabled(node.optBoolean("enabled", true));
    if (node.has("alpha")) view.setAlpha((float) node.optDouble("alpha", 1));
    if (node.has("elevation")) view.setElevation(dp(node.optInt("elevation")));
    if (node.has("background")) {
      GradientDrawable background = new GradientDrawable();
      if (node.optString("background").equals("gradient"))
        background =
            new GradientDrawable(GradientDrawable.Orientation.TL_BR, Appearance.gradient());
      else background.setColor(color(node.opt("background"), Color.TRANSPARENT));
      background.setCornerRadius(dp(node.optInt("radius", 24)));
      view.setBackground(
          node.optBoolean("ripple")
              ? new RippleDrawable(ColorStateList.valueOf(0x22526da9), background, null)
              : background);
    }
    if (node.has("tooltip")) view.setTooltipText(Appearance.text(node.optString("tooltip")));
    if (node.has("description"))
      view.setContentDescription(Appearance.text(node.optString("description")));
    JSONArray children = node.optJSONArray("children");
    if (children != null) {
      if (!(view instanceof ViewGroup))
        throw new IllegalArgumentException("Children require a container");
      for (int i = 0; i < children.length(); i++) {
        JSONObject child = children.optJSONObject(i);
        if (child == null) continue;
        View built = build(child, depth + 1);
        attach((ViewGroup) view, built, child);
      }
    }
    JSONObject action = node.optJSONObject("action");
    if (view instanceof Spinner) {
      JSONArray options = node.optJSONArray("options");
      ((Spinner) view)
          .setOnItemSelectedListener(
              new AdapterView.OnItemSelectedListener() {
                public void onNothingSelected(AdapterView<?> parent) {}

                public void onItemSelected(
                    AdapterView<?> parent, View selected, int index, long id) {
                  Object value = choice(options, index);
                  choiceValues.put(node.optString("id"), value);
                  if (action != null)
                    emit(action, CoreRuntime.args("index", index, "value", value));
                }
              });
    }
    if (view instanceof EditText) {
      JSONObject change = node.optJSONObject("onChange");
      if (change != null)
        ((EditText) view)
            .addTextChangedListener(
                host.watcher(
                    () ->
                        emit(
                            change,
                            CoreRuntime.args("value", ((EditText) view).getText().toString()))));
      JSONObject submit = node.optJSONObject("onSubmit");
      if (submit != null)
        ((EditText) view)
            .setOnEditorActionListener(
                (v, id, event) -> {
                  emit(submit, CoreRuntime.args("actionId", id));
                  return true;
                });
    }
    JSONObject hold = node.optJSONObject("onLongPress");
    if (hold != null)
      view.setOnLongClickListener(
          v -> {
            emit(hold, new JSONObject());
            return true;
          });
    if (action != null) {
      if (view instanceof SeekBar)
        ((SeekBar) view)
            .setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                  public void onStartTrackingTouch(SeekBar bar) {}

                  public void onStopTrackingTouch(SeekBar bar) {}

                  public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                    if (fromUser) emit(action, CoreRuntime.args("value", progress));
                  }
                });
      else if (view instanceof CompoundButton)
        ((CompoundButton) view)
            .setOnCheckedChangeListener(
                (v, checked) -> emit(action, CoreRuntime.args("checked", checked)));
      else if (!(view instanceof Spinner) && !(view instanceof SeekBar))
        view.setOnClickListener(v -> emit(action, new JSONObject()));
      view.setFocusable(true);
      if (node.optBoolean("atomic") && view instanceof ViewGroup) {
        passive((ViewGroup) view);
        ((ViewGroup) view).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
      }
      view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
      view.setAccessibilityDelegate(
          new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(
                View host, android.view.accessibility.AccessibilityNodeInfo info) {
              super.onInitializeAccessibilityNodeInfo(host, info);
              if (!(host instanceof CompoundButton)
                  && !(host instanceof Spinner)
                  && !(host instanceof SeekBar)) info.setClassName(Button.class.getName());
            }
          });
    }
    return view;
  }

  private void emit(JSONObject action, JSONObject values) {
    try {
      JSONObject event = new JSONObject(action.toString());
      event.put("fields", fields());
      for (Iterator<String> it = values.keys(); it.hasNext(); ) {
        String key = it.next();
        event.put(key, values.get(key));
      }
      events.accept(event);
    } catch (Exception e) {
      host.error(e);
    }
  }

  private Object choice(JSONArray options, int index) {
    if (options == null || index < 0 || index >= options.length()) return "";
    JSONObject o = options.optJSONObject(index);
    return o == null ? options.opt(index) : o.opt("value");
  }

  JSONObject fields() {
    JSONObject result = new JSONObject();
    try {
      for (Map.Entry<String, View> entry : refs.entrySet()) {
        View v = entry.getValue();
        if (v instanceof EditText) result.put(entry.getKey(), ((EditText) v).getText().toString());
        else if (v instanceof CompoundButton)
          result.put(entry.getKey(), ((CompoundButton) v).isChecked());
        else if (v instanceof SeekBar) result.put(entry.getKey(), ((SeekBar) v).getProgress());
      }
      for (Map.Entry<String, Object> e : choiceValues.entrySet())
        result.put(e.getKey(), e.getValue());
    } catch (JSONException e) {
      throw new IllegalStateException(e);
    }
    return result;
  }

  void apply(JSONObject patches) {
    for (Iterator<String> i = patches.keys(); i.hasNext(); ) {
      String id = i.next();
      View view = refs.get(id);
      JSONObject patch = patches.optJSONObject(id);
      if (view == null || patch == null) continue;
      if (patch.has("visible"))
        view.setVisibility(patch.optBoolean("visible") ? View.VISIBLE : View.GONE);
      if (patch.has("enabled")) view.setEnabled(patch.optBoolean("enabled"));
    }
  }

  void attach(ViewGroup parent, View child, JSONObject node) {
    if (parent instanceof ScrollView) {
      parent.addView(child, new ScrollView.LayoutParams(-1, -2));
      return;
    }
    LinearLayout.LayoutParams lp =
        new LinearLayout.LayoutParams(
            dimension(node.opt("width"), -1),
            dimension(node.opt("height"), -2),
            (float) node.optDouble("weight", 0));
    JSONArray margin = node.optJSONArray("margin");
    if (margin != null && margin.length() == 4)
      lp.setMargins(
          dp(margin.optInt(0)), dp(margin.optInt(1)), dp(margin.optInt(2)), dp(margin.optInt(3)));
    parent.addView(child, lp);
  }

  private int dimension(Object value, int fallback) {
    return value instanceof Number
        ? dp(((Number) value).intValue())
        : "wrap".equals(value) ? -2 : "fill".equals(value) ? -1 : fallback;
  }

  private int dp(int n) {
    return host.dp(n);
  }

  private int gravity(String name) {
    return name.equals("center")
        ? Gravity.CENTER
        : name.equals("end")
            ? Gravity.END | Gravity.CENTER_VERTICAL
            : name.equals("centerVertical") ? Gravity.CENTER_VERTICAL : Gravity.START | Gravity.TOP;
  }

  private int color(Object value, int fallback) {
    if (value instanceof Number) return Appearance.color(((Number) value).intValue());
    String token = String.valueOf(value);
    switch (token) {
      case "ink":
        return Appearance.ink();
      case "muted":
        return Appearance.muted();
      case "accent":
        return Appearance.accent();
      case "onAccent":
        return Appearance.onAccent();
      case "surface":
        return Appearance.surface();
      case "transparent":
        return Color.TRANSPARENT;
      default:
        try {
          return Color.parseColor(token);
        } catch (Exception e) {
          return fallback;
        }
    }
  }

  private static void passive(ViewGroup parent) {
    for (int i = 0; i < parent.getChildCount(); i++) {
      View child = parent.getChildAt(i);
      child.setTooltipText(null);
      child.setClickable(false);
      child.setLongClickable(false);
      child.setFocusable(false);
      child.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      if (child instanceof ViewGroup) passive((ViewGroup) child);
    }
  }
}
