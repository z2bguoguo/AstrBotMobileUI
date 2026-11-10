package io.github.astrbot.mobile.ui;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.*;
import android.text.method.*;
import android.view.*;
import android.widget.*;
import io.github.astrbot.mobile.core.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Platform-native UI; HTML is confined to plugin custom views. */
public final class MainActivity extends Activity {
  LinearLayout page, content, toolbar;
  private TextView subtitle;
  private Vault vault;
  private AppUpdater updater;
  private JSONObject state;
  private Connection selected;
  private ApiClient client;
  private HttpTransport transport;
  private List<Endpoint> endpoints = new ArrayList<>();
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final Set<String> suggestions = new LinkedHashSet<>();
  private int generation = 0;
  private boolean busy = false, autofill = true;
  private String filter = "";
  private Runnable backAction;
  private byte[] upload, download;
  private TextView uploadLabel;
  private Endpoint editing;
  private EditText paths, query, body, fileField;
  private JSONObject applyPreset;
  static int ACCENT = 0xff526da9, INK = 0xff252d3e, MUTED = 0xff667086;

  @Override
  protected void attachBaseContext(android.content.Context base) {
    super.attachBaseContext(Appearance.localized(base));
  }

  @Override
  public void onCreate(Bundle saved) {
    setTheme(
        Appearance.dark()
            ? io.github.astrbot.mobile.R.style.AppThemeDark
            : io.github.astrbot.mobile.R.style.AppTheme);
    super.onCreate(saved);
    ACCENT = Appearance.accent();
    INK = Appearance.ink();
    MUTED = Appearance.muted();
    vault = new Vault(this);
    try {
      state = vault.load();
      autofill = state.optBoolean("autofill", true);
      home();
      CoreStore.healthy();
      updater = new AppUpdater(this);
      updater.startup();
    } catch (Exception e) {
      new LocalizedDialogBuilder(this)
          .setTitle("无法读取本地配置")
          .setMessage("密钥或存储异常。配置未被覆盖。可在系统设置清除应用数据后重新配置。")
          .setPositiveButton("关闭", (d, w) -> finish())
          .setCancelable(false)
          .show();
    }
  }

  int dp(int n) {
    return (int) (n * getResources().getDisplayMetrics().density + .5f);
  }

  LinearLayout vertical() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(LinearLayout.VERTICAL);
    return l;
  }

  TextView label(String s, int size, int color) {
    TextView t = new TextView(this);
    t.setText(Appearance.text(s));
    t.setTooltipText(Appearance.text(s));
    t.setTextSize(size);
    t.setTextColor(Appearance.color(color));
    t.setPadding(0, dp(5), 0, dp(5));
    return t;
  }

  GradientDrawable background(int color, int radius) {
    GradientDrawable b = new GradientDrawable();
    b.setColor(Appearance.color(color));
    b.setCornerRadius(dp(radius));
    return b;
  }

  void screen(String title, String hint, Runnable back) {
    generation++;
    backAction = back;
    page = vertical();
    page.setBackground(
        new GradientDrawable(GradientDrawable.Orientation.TL_BR, Appearance.gradient()));
    // Target SDK 35 draws edge-to-edge: apply the actual system insets.
    page.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          v.setPadding(
              insets.getSystemWindowInsetLeft(),
              insets.getSystemWindowInsetTop(),
              insets.getSystemWindowInsetRight(),
              insets.getSystemWindowInsetBottom());
          return insets;
        });
    toolbar = vertical();
    toolbar.setPadding(dp(22), dp(14), dp(22), dp(8));
    if (back != null) {
      TextView b = label("‹  返回", 15, INK);
      b.setBackground(background(0xccffffff, 24));
      b.setPadding(dp(16), dp(8), dp(16), dp(8));
      b.setOnClickListener(v -> goBack());
      toolbar.addView(b);
    }
    TextView heading = label(title, 28, INK);
    heading.setTypeface(null, Typeface.BOLD);
    toolbar.addView(heading);
    subtitle = label(hint, 13, MUTED);
    toolbar.addView(subtitle);
    page.addView(toolbar);
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    content = vertical();
    content.setPadding(dp(18), dp(8), dp(18), dp(32));
    scroll.addView(content);
    page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    setContentView(page);
    page.requestApplyInsets();
  }

  LinearLayout card(String title, String desc) {
    LinearLayout c = vertical();
    c.setPadding(dp(18), dp(15), dp(18), dp(15));
    c.setBackground(background(0xcfffffff, 26));
    c.setElevation(dp(1));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.bottomMargin = dp(12);
    content.addView(c, lp);
    if (!title.isEmpty()) {
      TextView t = label(title, 18, INK);
      t.setTypeface(null, Typeface.BOLD);
      c.addView(t);
    }
    if (!desc.isEmpty()) c.addView(label(desc, 13, MUTED));
    return c;
  }

  Button button(LinearLayout parent, String title, Runnable action) {
    Button b = new Button(this);
    b.setText(Appearance.text(title));
    b.setTooltipText(Appearance.text(title));
    b.setTextSize(14);
    b.setTextColor(ACCENT);
    b.setAllCaps(false);
    b.setMinHeight(0);
    b.setMinWidth(0);
    b.setBackground(
        new android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(0x22526da9),
            background(0xffe8edf8, 24),
            null));
    b.setOnClickListener(
        v -> {
          if (!busy) attempt(action);
          else toast("正在处理，请稍候或取消");
        });
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(48));
    lp.topMargin = dp(8);
    parent.addView(b, lp);
    return b;
  }

  Button primaryButton(LinearLayout parent, String title, Runnable action) {
    Button b = button(parent, title, action);
    b.setTextColor(Appearance.onAccent());
    b.setTypeface(null, Typeface.BOLD);
    b.setBackground(
        new android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(0x33ffffff), background(ACCENT, 24), null));
    return b;
  }

  LinearLayout group(LinearLayout parent) {
    LinearLayout g = vertical();
    g.setBackground(background(0xd9ffffff, 26));
    g.setPadding(0, dp(4), 0, dp(4));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.bottomMargin = dp(10);
    parent.addView(g, lp);
    return g;
  }

  void navRow(LinearLayout parent, String title, String detail, String icon, Runnable action) {
    LinearLayout row = new LinearLayout(this);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(dp(16), dp(15), dp(16), dp(15));
    row.setMinimumHeight(dp(70));
    if (!icon.isEmpty()) {
      TextView symbol = label(icon, 20, ACCENT);
      symbol.setGravity(Gravity.CENTER);
      symbol.setBackground(background(0xffe6ecfa, 16));
      row.addView(symbol, new LinearLayout.LayoutParams(dp(40), dp(40)));
    }
    LinearLayout text = vertical();
    text.setPadding(icon.isEmpty() ? 0 : dp(13), 0, dp(12), 0);
    TextView name = label(title, 16, INK);
    name.setTypeface(null, Typeface.BOLD);
    text.addView(name);
    if (!detail.isEmpty()) text.addView(label(detail, 12, MUTED));
    row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
    if (action != null) {
      TextView arrow = label("›", 25, MUTED);
      row.addView(arrow);
      row.setBackground(
          new android.graphics.drawable.RippleDrawable(
              android.content.res.ColorStateList.valueOf(0x15526da9),
              background(Color.TRANSPARENT, 20),
              null));
      row.setOnClickListener(
          v -> {
            if (!busy) attempt(action);
            else toast("正在处理，请稍候");
          });
    }
    parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
  }

  void textAction(LinearLayout parent, String title, Runnable action) {
    TextView t = label(title, 13, ACCENT);
    t.setGravity(Gravity.CENTER);
    t.setPadding(dp(12), dp(12), dp(12), dp(12));
    t.setOnClickListener(v -> attempt(action));
    parent.addView(t);
  }

  void editLongText(
      String title, String value, boolean json, java.util.function.Consumer<String> applied) {
    EditText target = new EditText(this);
    target.setText(value);
    target.addTextChangedListener(watcher(() -> applied.accept(target.getText().toString())));
    fullEditor(title, target, json);
  }

  private void bottomNav(boolean connected, String current) {
    LinearLayout bar = new LinearLayout(this);
    bar.setPadding(dp(12), dp(6), dp(12), dp(10));
    bar.setBackground(background(0xe6f9fbff, 24));
    String[] titles = connected ? new String[] {"总览", "管理", "工具"} : new String[] {"连接", "预设", "设置"};
    Runnable[] actions =
        connected
            ? new Runnable[] {this::overview, this::workspace, this::advancedTools}
            : new Runnable[] {this::home, this::presets, this::appSettings};
    for (int i = 0; i < titles.length; i++) {
      String name = titles[i];
      Runnable action = actions[i];
      TextView tab = label(name, 14, current.equals(name) ? ACCENT : MUTED);
      tab.setGravity(Gravity.CENTER);
      tab.setPadding(dp(8), dp(14), dp(8), dp(14));
      if (current.equals(name)) tab.setBackground(background(0xffe3eafb, 22));
      tab.setOnClickListener(
          v -> {
            if (!busy) attempt(action);
          });
      bar.addView(tab, new LinearLayout.LayoutParams(0, -2, 1));
    }
    page.addView(bar);
  }

  EditText input(
      LinearLayout parent, String hint, String value, boolean secret, boolean multiline) {
    TextView caption = label(hint, 13, MUTED);
    parent.addView(caption);
    AutoCompleteTextView e = new AutoCompleteTextView(this);
    e.setTag(caption);
    e.setText(value);
    e.setTextColor(INK);
    e.setHintTextColor(MUTED);
    e.setTextSize(15);
    e.setTextColor(INK);
    e.setSingleLine(!multiline);
    e.setPadding(dp(14), dp(13), dp(14), dp(13));
    e.setBackground(background(0xffeef2f9, 16));
    e.setInputType(
        secret
            ? android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            : android.text.InputType.TYPE_CLASS_TEXT
                | (multiline ? android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
    if (secret) e.setTransformationMethod(PasswordTransformationMethod.getInstance());
    if (multiline) {
      e.setGravity(Gravity.TOP);
      e.setMinLines(3);
      e.setMaxLines(9);
    }
    if (autofill && !secret) {
      e.setThreshold(1);
      e.setAdapter(
          new ArrayAdapter<>(
              this, android.R.layout.simple_dropdown_item_1line, new ArrayList<>(suggestions)));
    }
    parent.addView(e, new LinearLayout.LayoutParams(-1, -2));
    return e;
  }

  void toast(String text) {
    Toast.makeText(this, Appearance.text(text), Toast.LENGTH_LONG).show();
  }

  private void attempt(Runnable action) {
    try {
      action.run();
    } catch (Exception e) {
      error(e);
    }
  }

  void error(Throwable e) {
    if (!isFinishing())
      new LocalizedDialogBuilder(this)
          .setTitle("操作未完成")
          .setMessage(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())
          .setPositiveButton("知道了", null)
          .setNeutralButton(
              "复制错误详情",
              (d, w) -> {
                android.content.ClipboardManager clipboard =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(
                    ClipData.newPlainText(
                        "错误详情",
                        e.getClass().getSimpleName()
                            + ": "
                            + e.getMessage()
                            + "\nAndroid "
                            + android.os.Build.VERSION.RELEASE));
                toast("错误详情已复制");
              })
          .show();
  }

  private interface Task<T> {
    T run() throws Exception;
  }

  private interface Done<T> {
    void accept(T value) throws Exception;
  }

  private void fieldVisibility(EditText field, boolean visible) {
    field.setVisibility(visible ? View.VISIBLE : View.GONE);
    if (field.getTag() instanceof View)
      ((View) field.getTag()).setVisibility(visible ? View.VISIBLE : View.GONE);
  }

  private <T> void async(Task<T> task, Done<T> done) {
    async(task, done, null);
  }

  private <T> void async(Task<T> task, Done<T> done, Runnable failed) {
    if (busy) {
      toast("已有任务正在处理");
      return;
    }
    busy = true;
    int view = generation;
    subtitle.setText(Appearance.text("正在连接服务器…"));
    worker.submit(
        () -> {
          try {
            T result = task.run();
            runOnUiThread(
                () -> {
                  busy = false;
                  if (view == generation && !isFinishing())
                    try {
                      subtitle.setText(Appearance.text("操作完成"));
                      done.accept(result);
                    } catch (Exception e) {
                      error(e);
                    }
                });
          } catch (Exception e) {
            runOnUiThread(
                () -> {
                  busy = false;
                  if (view == generation && !isFinishing()) {
                    subtitle.setText(Appearance.text("操作未完成"));
                    if (failed != null) failed.run();
                    error(e);
                  }
                });
          }
        });
  }

  private void persist() throws Exception {
    vault.save(state);
  }

  private JSONArray list(String key) {
    return state.optJSONArray(key) == null ? new JSONArray() : state.optJSONArray(key);
  }

  void confirm(String title, String message, Runnable yes) {
    new LocalizedDialogBuilder(this)
        .setTitle(title)
        .setMessage(message)
        .setNegativeButton("取消", null)
        .setPositiveButton("确认", (d, w) -> attempt(yes))
        .show();
  }

  private void home() {
    selected = null;
    client = null;
    suggestions.clear();
    screen("AstrBot Mobile", "连接你的机器人工作空间", null);
    LinearLayout intro = card("随时连接，轻松管理", "在不同实例间切换，让配置、提示词和工具井井有条。");
    primaryButton(intro, "＋ 添加连接", () -> connectionForm(null));
    LinearLayout search = card("我的连接", "");
    EditText f = input(search, "搜索名称、分组或地址", filter, false, false);
    LinearLayout holder = vertical();
    content.addView(holder);
    renderConnections(holder, filter);
    f.addTextChangedListener(
        watcher(
            () -> {
              filter = f.getText().toString();
              renderConnections(holder, filter);
            }));
    bottomNav(false, "连接");
  }

  private void appSettings() {
    screen("应用设置", "管理本机偏好与诊断", this::home);
    LinearLayout appearance = card("Appearance / 外观", "Language · Theme · Accent / 语言 · 主题 · 配色");
    Appearance.settings(this, appearance);
    LinearLayout preferences = card("输入体验", "");
    Switch assist = new Switch(this);
    assist.setText(Appearance.text("输入辅助与自动补全"));
    assist.setTextColor(INK);
    assist.setTextSize(16);
    assist.setPadding(dp(4), dp(12), dp(4), dp(12));
    assist.setChecked(autofill);
    preferences.addView(assist);
    assist.setOnCheckedChangeListener(
        (v, c) -> {
          autofill = c;
          try {
            state.put("autofill", c);
            persist();
          } catch (Exception e) {
            error(e);
          }
        });
    LinearLayout tools = group(content);
    navRow(tools, "预设库", "复用配置与长文本", "≡", this::presets);
    navRow(tools, "客户端插件", "管理扩展操作", "◇", this::extensions);
    LinearLayout updates = card("应用更新", "");
    if (updater != null) updater.settings(updates);
    LinearLayout about = card("关于与诊断", "");
    navRow(about, "截图与录屏", "允许，用于反馈与排查问题", "◎", null);
    navRow(
        about,
        "使用指南",
        "连接、配置与插件说明",
        "?",
        () ->
            startActivity(
                new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://z2bguoguo.github.io/AstrBotMobileUI/"))));
    bottomNav(false, "设置");
  }

  void refreshUpdateSettings() {
    appSettings();
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (updater != null) updater.resume();
  }

  TextWatcher watcher(Runnable r) {
    return new TextWatcher() {
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

      public void onTextChanged(CharSequence s, int start, int before, int count) {
        r.run();
      }

      public void afterTextChanged(Editable e) {}
    };
  }

  private void renderConnections(LinearLayout holder, String text) {
    holder.removeAllViews();
    String last = "";
    List<Connection> entries = new ArrayList<>();
    JSONArray a = list("connections");
    for (int i = 0; i < a.length(); i++) entries.add(Connection.fromJson(a.optJSONObject(i)));
    entries.sort(Comparator.comparing((Connection c) -> c.group).thenComparing(c -> c.name));
    for (Connection c : entries) {
      if (!(c.name + c.group + c.url)
          .toLowerCase(Locale.ROOT)
          .contains(text.toLowerCase(Locale.ROOT))) continue;
      if (!last.equals(c.group)) {
        holder.addView(label(c.group, 17, MUTED));
        last = c.group;
      }
      LinearLayout row = group(holder);
      navRow(row, c.name, c.url, "◉", () -> connect(c));
      textAction(row, "连接设置", () -> connectionForm(c));
    }
    if (entries.isEmpty()) holder.addView(label("还没有连接。添加你的第一台 AstrBot。", 15, MUTED));
  }

  private void connectionForm(Connection original) {
    screen(
        original == null ? "新建连接" : "连接设置",
        "可填写反向代理子路径，例如 https://bot.example.com/astrbot",
        this::home);
    Connection c = original == null ? new Connection() : original;
    LinearLayout form = card("基本信息", "");
    EditText name = input(form, "名称", c.name, false, false),
        group = input(form, "分组", c.group, false, false),
        url = input(form, "服务地址", c.url, false, false);
    EditText user = input(form, "WebUI 用户名", c.username, false, false),
        pass = input(form, "WebUI 密码", c.password, true, false),
        key = input(form, "API Key（可选，填写后优先使用）", c.apiKey, true, false);
    RadioGroup authentication = new RadioGroup(this);
    authentication.setOrientation(RadioGroup.HORIZONTAL);
    RadioButton accountMode = new RadioButton(this);
    accountMode.setId(View.generateViewId());
    accountMode.setText(Appearance.text("账户密码"));
    RadioButton keyMode = new RadioButton(this);
    keyMode.setId(View.generateViewId());
    keyMode.setText(io.github.astrbot.mobile.R.string.authentication_key);
    authentication.addView(accountMode);
    authentication.addView(keyMode);
    form.addView(authentication, 0);
    fieldVisibility(user, c.apiKey.isBlank());
    fieldVisibility(pass, c.apiKey.isBlank());
    fieldVisibility(key, !c.apiKey.isBlank());
    authentication.check(c.apiKey.isBlank() ? accountMode.getId() : keyMode.getId());
    authentication.setOnCheckedChangeListener(
        (g, id) -> {
          boolean account = id == accountMode.getId();
          fieldVisibility(user, account);
          fieldVisibility(pass, account);
          fieldVisibility(key, !account);
        });
    Spinner mode = new Spinner(this);
    mode.setAdapter(
        new ArrayAdapter<>(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            new String[] {"原文密码（新版默认）", "MD5 密码（部分旧版）"}));
    mode.setSelection(c.passwordMode.equals("md5") ? 1 : 0);
    form.addView(mode);
    form.addView(label("HTTP 会明文传输凭据，建议使用 HTTPS 或可信局域网。API Key 权限不足时可改用 WebUI 账户。", 13, MUTED));
    primaryButton(
        form,
        "保存连接",
        () -> {
          try {
            String normalized = Connection.normalizeUrl(url.getText().toString());
            Connection n = Connection.fromJson(c.toJson());
            n.name = name.getText().toString();
            n.group = group.getText().toString();
            n.url = normalized;
            n.username = user.getText().toString();
            n.password = pass.getText().toString();
            n.apiKey =
                authentication.getCheckedRadioButtonId() == keyMode.getId()
                    ? key.getText().toString()
                    : "";
            n.passwordMode = mode.getSelectedItemPosition() == 1 ? "md5" : "plain";
            JSONArray a = list("connections");
            boolean found = false;
            for (int i = 0; i < a.length(); i++)
              if (a.getJSONObject(i).optString("id").equals(n.id)) {
                a.put(i, n.toJson());
                found = true;
                break;
              }
            if (!found) a.put(n.toJson());
            state.put("connections", a);
            persist();
            home();
          } catch (Exception e) {
            error(e);
          }
        });
    if (original != null) {
      button(
          form,
          "复制已保存的连接",
          () -> {
            try {
              list("connections").put(c.duplicate().toJson());
              persist();
              home();
            } catch (Exception e) {
              error(e);
            }
          });
      button(
          form,
          "删除连接",
          () ->
              confirm(
                  "删除连接",
                  c.name + " 的本地密码也会删除。",
                  () -> {
                    try {
                      JSONArray a = list("connections");
                      for (int i = a.length() - 1; i >= 0; i--)
                        if (a.getJSONObject(i).optString("id").equals(c.id)) a.remove(i);
                      persist();
                      home();
                    } catch (Exception e) {
                      error(e);
                    }
                  }));
    }
  }

  private JSONObject asset(String name) throws Exception {
    return new JSONObject(
        new String(
            HttpTransport.readLimited(getAssets().open(name), 8 * 1024 * 1024),
            StandardCharsets.UTF_8));
  }

  private void connect(Connection c) {
    selected = c;
    suggestions.clear();
    transport = new HttpTransport();
    client = new ApiClient(c, transport);
    screen(c.name, "正在检测接口能力", this::home);
    EditText otp = input(card("两步验证", "未启用两步验证时留空"), "验证码", "", true, false);
    button(content, "连接并登录", () -> beginLogin(otp.getText().toString()));
    beginLogin("");
  }

  private void beginLogin(String otp) {
    ApiClient target = client;
    async(
        () -> {
          target.discover(asset("openapi.json"));
          target.login(otp);
          return target.modern
              ? Endpoint.fromSpec(target.spec)
              : Endpoint.fromLegacy(
                  new JSONArray(
                      new String(
                          HttpTransport.readLimited(
                              getAssets().open("legacy.json"), 4 * 1024 * 1024),
                          StandardCharsets.UTF_8)));
        },
        v -> {
          endpoints = v;
          workspace();
        });
  }

  private static final Map<String, String> CATEGORIES =
      Map.ofEntries(
          Map.entry("Auth", "账户与认证"),
          Map.entry("System Stats", "概览与运行状态"),
          Map.entry("Config Profiles", "配置与路由"),
          Map.entry("Providers", "模型服务"),
          Map.entry("Plugins", "AstrBot 插件"),
          Map.entry("Personas", "人格与提示词"),
          Map.entry("Tools", "工具与 MCP"),
          Map.entry("Chat", "对话"),
          Map.entry("Knowledge Bases", "知识库"),
          Map.entry("Sessions", "会话管理"),
          Map.entry("Backups", "备份与恢复"),
          Map.entry("stats", "概览与运行状态"),
          Map.entry("tools", "工具与 MCP"),
          Map.entry("personas", "人格与提示词"),
          Map.entry("config_profiles", "配置与路由"),
          Map.entry("plugins", "AstrBot 插件"));

  private void workspace() {
    screen(selected.name, "已连接 · " + selected.group, this::home);
    LinearLayout settings = card("配置与运行", "");
    navRow(settings, "通用设置", "基础配置、管理员与运行选项", "⚙", () -> openConfiguration("system", "通用设置"));
    navRow(settings, "配置文件", "不同场景使用不同配置", "▤", this::profiles);
    LinearLayout ai = card("对话与智能", "");
    navRow(ai, "人格与提示词", "编辑系统提示词与开场对话", "✦", () -> resources(ManagementApi.Resource.PERSONAS));
    navRow(ai, "模型服务", "查看与编辑模型连接", "◈", () -> resources(ManagementApi.Resource.PROVIDERS));
    navRow(ai, "MCP 服务", "连接地址、启动参数与环境变量", "⌘", () -> resources(ManagementApi.Resource.MCP));
    LinearLayout bots = card("平台与扩展", "");
    navRow(bots, "机器人平台", "编辑平台连接与运行设置", "◉", () -> resources(ManagementApi.Resource.BOTS));
    navRow(bots, "插件管理", "启停插件、配置与自定义页面", "◇", () -> resources(ManagementApi.Resource.PLUGINS));
    bottomNav(true, "管理");
  }

  private void advancedTools() {
    screen("工具", "预设、扩展与高级诊断", this::workspace);
    LinearLayout everyday = group(content);
    navRow(everyday, "预设与配置传递", "保存常用设置，在实例间复用", "≡", this::presets);
    navRow(everyday, "客户端插件", "管理客户端扩展", "◇", this::extensions);
    navRow(everyday, "插件自定义页面", "打开 HTML 配置页面", "▣", this::pluginEntry);
    LinearLayout advanced = card("高级工具", "用于未适配的功能及开发诊断");
    navRow(advanced, "接口浏览器", "搜索全部接口操作", "⌘", () -> operations(null));
    navRow(advanced, "自定义请求", "手动指定接口和参数", "↗", this::manualEndpoint);
    bottomNav(true, "工具");
  }

  private void overview() {
    screen(selected.name, "运行概览", this::workspace);
    LinearLayout status = card("已连接", selected.group);
    navRow(status, "服务地址", selected.url, "◉", null);
    LinearLayout stats = card("运行数据", "正在读取…");
    bottomNav(true, "总览");
    async(
        () -> new ManagementApi(client).overview(),
        data -> {
          stats.removeAllViews();
          renderInformation(stats, data, 0);
        });
  }

  private void renderInformation(LinearLayout target, Object value, int depth) throws Exception {
    if (value instanceof JSONObject && depth < 3) {
      JSONObject o = (JSONObject) value;
      for (Iterator<String> i = o.keys(); i.hasNext(); ) {
        String key = i.next();
        Object v = o.get(key);
        if (v instanceof JSONObject) renderInformation(target, v, depth + 1);
        else if (!(v instanceof JSONArray)) navRow(target, fieldName(key), v.toString(), "", null);
      }
    } else target.addView(label(Helpers.pretty(Helpers.redact(value)), 14, INK));
  }

  private String fieldName(String key) {
    return Map.ofEntries(
            Map.entry("version", "版本"),
            Map.entry("platform_count", "平台数量"),
            Map.entry("plugin_count", "插件数量"),
            Map.entry("message_count", "消息数量"),
            Map.entry("total_message_count", "累计消息"),
            Map.entry("uptime", "运行时长"),
            Map.entry("start_time", "启动时间"),
            Map.entry("dashboard_version", "管理面板版本"))
        .getOrDefault(key, key.replace('_', ' '));
  }

  private void profiles() {
    screen("配置文件", "选择要编辑的配置", this::workspace);
    LinearLayout rows = group(content);
    primaryButton(content, "刷新列表", this::profiles);
    async(
        () -> new ManagementApi(client).profiles(),
        items -> {
          for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            String id = item.optString("id"), name = item.optString("name", id);
            navRow(
                rows,
                name,
                id.equals("default") ? "默认配置" : "场景配置",
                "▤",
                () -> openConfiguration(id, name));
          }
        });
  }

  private void openConfiguration(String id, String title) {
    screen(title, "正在读取现有设置…", this::workspace);
    button(content, "重新读取", () -> openConfiguration(id, title));
    ManagementApi api = new ManagementApi(client);
    async(
        () -> api.configuration(id),
        doc ->
            showSettings(
                title,
                doc,
                Set.of(),
                this::workspace,
                (config, changes) ->
                    async(
                        () -> {
                          api.saveConfiguration(id, config);
                          return true;
                        },
                        ok -> {
                          toast("配置已保存");
                          workspace();
                        })));
  }

  private void showSettings(
      String title,
      JSONObject document,
      Set<String> readOnly,
      Runnable back,
      SettingsEditor.Save save)
      throws Exception {
    JSONObject config = document.optJSONObject("config");
    if (config == null) throw new IllegalArgumentException("此项没有可编辑配置，请检查服务器版本或插件配置声明");
    new SettingsEditor(
            this,
            title,
            config,
            document.optJSONObject("metadata"),
            Appearance.labels(),
            readOnly,
            back,
            save)
        .show();
  }

  private void resources(ManagementApi.Resource type) {
    screen(type.title, "选择条目，直接编辑现有设置", this::workspace);
    LinearLayout rows = vertical();
    content.addView(rows);
    button(content, "刷新列表", () -> resources(type));
    if (type == ManagementApi.Resource.PERSONAS || type == ManagementApi.Resource.MCP)
      primaryButton(
          content,
          "＋ 新建" + (type == ManagementApi.Resource.MCP ? "服务" : "人格"),
          () -> createResource(type));
    async(
        () -> new ManagementApi(client).list(type),
        items -> {
          if (items.length() == 0) rows.addView(label("还没有条目，可添加新的配置。", 14, MUTED));
          for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            String id = ManagementApi.id(type, item),
                name = item.optString("display_name", item.optString("name", id));
            LinearLayout group = group(rows);
            String summary =
                item.optString(
                    "desc",
                    type == ManagementApi.Resource.MCP
                        ? (item.optBoolean("connected") ? "已连接" : "未连接")
                        : "点击编辑设置");
            navRow(group, name, summary, "◇", () -> editResource(type, item));
            if (type == ManagementApi.Resource.PLUGINS) {
              JSONArray pages = item.optJSONArray("views");
              if (pages == null) pages = item.optJSONArray("pages");
              if (pages != null)
                for (int n = 0; n < pages.length(); n++) {
                  JSONObject viewInfo = pages.optJSONObject(n);
                  String view =
                      viewInfo == null
                          ? pages.optString(n)
                          : viewInfo.optString(
                              "name",
                              viewInfo.optString("pageName", viewInfo.optString("view_name")));
                  navRow(group, "打开 " + view, "插件自定义页面", "▣", () -> openPluginPage(id, view));
                }
              Switch toggle = new Switch(this);
              toggle.setText(Appearance.text("启用插件"));
              toggle.setPadding(dp(16), dp(8), dp(16), dp(12));
              toggle.setTextColor(INK);
              toggle.setChecked(item.optBoolean("activated"));
              group.addView(toggle);
              boolean[] reverting = {false};
              toggle.setOnCheckedChangeListener(
                  (b, enabled) -> {
                    if (reverting[0]) return;
                    if (busy) {
                      reverting[0] = true;
                      toggle.setChecked(!enabled);
                      reverting[0] = false;
                      return;
                    }
                    toggle.setEnabled(false);
                    async(
                        () -> {
                          new ManagementApi(client).pluginEnabled(id, enabled);
                          return true;
                        },
                        ok -> {
                          toggle.setEnabled(true);
                          toast(enabled ? "插件已启用" : "插件已停用");
                        },
                        () -> {
                          reverting[0] = true;
                          toggle.setChecked(!enabled);
                          reverting[0] = false;
                          toggle.setEnabled(true);
                        });
                  });
            }
          }
        });
  }

  private void editResource(ManagementApi.Resource type, JSONObject item) {
    String id = ManagementApi.id(type, item);
    screen(type.title, "正在读取现有设置…", () -> resources(type));
    button(content, "重新读取", () -> editResource(type, item));
    ManagementApi api = new ManagementApi(client);
    async(
        () -> api.detail(type, item),
        doc ->
            showSettings(
                id,
                doc,
                Set.of("id", "persona_id"),
                () -> resources(type),
                (config, changes) ->
                    async(
                        () -> {
                          api.save(type, id, config, false);
                          return true;
                        },
                        ok -> {
                          toast("设置已保存");
                          resources(type);
                        })));
  }

  private void createResource(ManagementApi.Resource type) {
    LinearLayout panel = vertical();
    panel.setPadding(dp(20), 0, dp(20), 0);
    EditText name =
        input(panel, type == ManagementApi.Resource.MCP ? "服务名称" : "人格标识", "", false, false);
    AlertDialog dialog =
        new LocalizedDialogBuilder(this)
            .setTitle(type == ManagementApi.Resource.MCP ? "新建 MCP 服务" : "新建人格")
            .setView(panel)
            .setNegativeButton("取消", null)
            .setPositiveButton("继续", null)
            .create();
    dialog.setOnShowListener(
        d ->
            dialog
                .getButton(-1)
                .setOnClickListener(
                    v -> {
                      String id = name.getText().toString().trim();
                      if (id.isEmpty()) {
                        name.setError("名称不能为空");
                        return;
                      }
                      dialog.dismiss();
                      if (type == ManagementApi.Resource.MCP)
                        new LocalizedDialogBuilder(this)
                            .setTitle("选择连接方式")
                            .setItems(
                                new String[] {"远程地址（HTTP / SSE）", "启动命令（stdio）"},
                                (choice, index) -> newResourceDraft(type, id, index == 0))
                            .setNegativeButton("取消", null)
                            .show();
                      else newResourceDraft(type, id, false);
                    }));
    dialog.show();
  }

  private void newResourceDraft(ManagementApi.Resource type, String id, boolean remote) {
    try {
      JSONObject config;
      if (type == ManagementApi.Resource.PERSONAS)
        config =
            new JSONObject()
                .put("system_prompt", "")
                .put("begin_dialogs", new JSONArray())
                .put("tools", new JSONArray())
                .put("skills", new JSONArray());
      else if (remote)
        config =
            new JSONObject().put("active", true).put("url", "").put("headers", new JSONObject());
      else
        config =
            new JSONObject()
                .put("active", true)
                .put("command", "")
                .put("args", new JSONArray())
                .put("env", new JSONObject());
      JSONObject doc = new JSONObject().put("config", config);
      showSettings(
          id,
          doc,
          Set.of(),
          () -> resources(type),
          (draft, changes) ->
              async(
                  () -> {
                    new ManagementApi(client).save(type, id, draft, true);
                    return true;
                  },
                  ok -> {
                    toast("创建成功");
                    resources(type);
                  }));
    } catch (Exception e) {
      error(e);
    }
  }

  private void quickRead(String modern, String old) {
    for (Endpoint e : endpoints)
      if (e.path.equals(client.modern ? modern : old) && e.method.equals("GET")) {
        operation(e, null);
        return;
      }
    toast("此服务器没有对应接口，请从操作列表选择");
  }

  private void operations(String category) {
    screen(
        category == null ? "全部操作" : CATEGORIES.getOrDefault(category, category),
        "搜索功能名称、HTTP 方法或接口路径",
        this::workspace);
    EditText search = input(card("查找操作", ""), "关键词", "", false, false);
    LinearLayout rows = vertical();
    content.addView(rows);
    Runnable render =
        () -> {
          rows.removeAllViews();
          String q = search.getText().toString().toLowerCase(Locale.ROOT);
          for (Endpoint e : endpoints)
            if ((category == null || category.equals(e.category))
                && (e.title + e.path + e.method).toLowerCase(Locale.ROOT).contains(q)) {
              Button b =
                  button(rows, e.title + "\n" + e.method + " " + e.path, () -> operation(e, null));
              b.setTextSize(12);
              b.setMinHeight(dp(68));
              b.setHeight(dp(72));
            }
        };
    search.addTextChangedListener(watcher(render));
    render.run();
  }

  private void manualEndpoint() {
    screen("手动 API", "用于服务器新增或旧版未收录接口；凭据只发送至当前实例", this::workspace);
    LinearLayout form = card("请求", "");
    EditText path = input(form, "路径（/api/ 开头）", "/api/", false, false);
    Spinner method = new Spinner(this);
    method.setAdapter(
        new ArrayAdapter<>(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            new String[] {"GET", "POST", "PUT", "PATCH", "DELETE"}));
    form.addView(method);
    button(
        form,
        "编辑请求",
        () ->
            operation(
                new Endpoint(
                    path.getText().toString(),
                    method.getSelectedItem().toString(),
                    new JSONObject()),
                null));
  }

  private JSONObject object(EditText e) throws Exception {
    return new JSONObject(e.getText().toString().trim().isEmpty() ? "{}" : e.getText().toString());
  }

  private void operation(Endpoint e, JSONObject preset) {
    editing = e;
    upload = null;
    download = null;
    screen(e.title, e.method + " " + e.path, () -> operations(e.category));
    LinearLayout intro =
        card(
            "请求说明",
            e.definition.optString(
                "description",
                client.modern ? "参数来自服务器 OpenAPI 契约。" : "旧接口字段依服务器版本而异，请参考现有配置或手动填写。"));
    if (e.mutates()) intro.addView(label("写入前会展示确认。不同实例的标识、插件及版本可能不同，传递预设前请检查。", 13, 0xffac5c23));
    try {
      JSONObject pathDefaults = new JSONObject(), queryDefaults = new JSONObject();
      java.util.regex.Matcher m =
          java.util.regex.Pattern.compile("\\{([^}:]+)(?::[^}]+)?}").matcher(e.path);
      while (m.find()) pathDefaults.put(m.group(1), "");
      JSONArray params = e.definition.optJSONArray("parameters");
      if (params != null)
        for (int i = 0; i < params.length(); i++) {
          JSONObject p = Schema.resolve(params.getJSONObject(i), client.spec);
          if (p.optString("in").equals("query") && p.optBoolean("required"))
            queryDefaults.put(
                p.getString("name"),
                Schema.sample(
                    p.optJSONObject("schema") == null
                        ? new JSONObject()
                        : p.getJSONObject("schema"),
                    client.spec,
                    0));
        }
      JSONObject schema = Schema.requestSchema(e, client.spec);
      Object sample = Schema.sample(schema, client.spec, 0);
      JSONObject bodyDefault =
          sample instanceof JSONObject ? (JSONObject) sample : new JSONObject();
      LinearLayout form = card("参数", "路径参数与查询参数使用 JSON 对象。所有字段均可进入全屏编辑。");
      paths =
          jsonField(
              form, "路径参数", preset == null ? pathDefaults : preset.optJSONObject("pathParams"));
      query =
          jsonField(form, "查询参数", preset == null ? queryDefaults : preset.optJSONObject("query"));
      body = jsonField(form, "请求内容", preset == null ? bodyDefault : preset.optJSONObject("body"));
      button(form, "辅助表单 · 按字段编辑", () -> structured(body, schema));
      if (params != null && params.length() > 0)
        button(
            form,
            "查看参数说明",
            () -> {
              try {
                textViewer("参数说明", OperationInfo.describe(e, client.spec));
              } catch (Exception x) {
                error(x);
              }
            });
      LinearLayout files = card("文件与结果", "上传上限 8 MiB，响应上限 16 MiB；大文件请使用 WebUI。流式/二进制专用协议尚需对应扩展。");
      fileField = input(files, "上传字段名", "file", false, false);
      uploadLabel = label("未选择文件", 13, MUTED);
      files.addView(uploadLabel);
      button(
          files,
          "选择文件（可选）",
          () -> {
            Intent i =
                new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .setType("*/*")
                    .addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(i, 10);
          });
      button(content, e.mutates() ? "预览并执行" : "读取数据", () -> execute(e));
      button(content, "保存为预设 / 传递到其他实例", () -> savePreset(e));
    } catch (Exception x) {
      error(x);
    }
  }

  private EditText jsonField(LinearLayout parent, String title, JSONObject value) throws Exception {
    EditText e = input(parent, title, value == null ? "{}" : value.toString(2), false, true);
    e.setTypeface(Typeface.MONOSPACE);
    button(parent, "展开编辑 · " + title, () -> fullEditor(title, e, true));
    return e;
  }

  private void structured(EditText target, JSONObject schema) {
    try {
      JSONObject value = object(target),
          properties = Schema.resolve(schema, client.spec).optJSONObject("properties");
      if (properties != null)
        for (Iterator<String> i = properties.keys(); i.hasNext(); ) {
          String k = i.next();
          if (!value.has(k))
            value.put(k, Schema.sample(properties.getJSONObject(k), client.spec, 0));
        }
      LinearLayout panel = vertical();
      panel.setPadding(dp(18), dp(8), dp(18), dp(8));
      Map<String, EditText> fields = new LinkedHashMap<>();
      for (Iterator<String> i = value.keys(); i.hasNext(); ) {
        String k = i.next();
        Object v = value.get(k);
        EditText field = input(panel, k, Helpers.pretty(v), false, true);
        fields.put(k, field);
        JSONObject prop = properties == null ? null : properties.optJSONObject(k);
        if (prop != null) {
          prop = Schema.resolve(prop, client.spec);
          String desc = prop.optString("description");
          if (!desc.isBlank()) panel.addView(label(desc, 12, MUTED));
          JSONArray options = prop.optJSONArray("enum");
          if (options != null) {
            Spinner picker = new Spinner(this);
            List<String> items = new ArrayList<>();
            items.add("选择建议值…");
            for (int n = 0; n < options.length(); n++) items.add(options.get(n).toString());
            picker.setAdapter(
                new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items));
            picker.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                  public void onNothingSelected(AdapterView<?> p) {}

                  public void onItemSelected(AdapterView<?> p, View v, int index, long id) {
                    if (index > 0) field.setText(items.get(index));
                  }
                });
            panel.addView(picker);
          }
        }
        button(
            panel,
            "全屏编辑 · " + k,
            () -> fullEditor(k, field, v instanceof JSONObject || v instanceof JSONArray));
        TextView warning = label("", 12, 0xffac5c23);
        panel.addView(warning);
        field.addTextChangedListener(
            watcher(
                () ->
                    warning.setText(
                        String.join("\n", Helpers.warnings(k, field.getText().toString())))));
      }
      ScrollView scroll = new ScrollView(this);
      scroll.addView(panel);
      AlertDialog dialog =
          new LocalizedDialogBuilder(this)
              .setTitle("字段编辑")
              .setView(scroll)
              .setNegativeButton("取消", null)
              .setPositiveButton("应用", null)
              .create();
      dialog.setOnShowListener(
          d ->
              dialog
                  .getButton(-1)
                  .setOnClickListener(
                      v -> {
                        try {
                          JSONObject result = new JSONObject(value.toString());
                          for (Map.Entry<String, EditText> entry : fields.entrySet()) {
                            String k = entry.getKey(), text = entry.getValue().getText().toString();
                            Object before = value.get(k), after = text;
                            if (before instanceof JSONObject) after = new JSONObject(text);
                            else if (before instanceof JSONArray) after = new JSONArray(text);
                            else if (before instanceof Boolean) {
                              if (!text.equals("true") && !text.equals("false"))
                                throw new IllegalArgumentException(k + " 需要 true 或 false");
                              after = Boolean.valueOf(text);
                            } else if (before instanceof Number)
                              after = new java.math.BigDecimal(text);
                            result.put(k, after);
                          }
                          target.setText(result.toString(2));
                          dialog.dismiss();
                        } catch (Exception x) {
                          error(x);
                        }
                      }));
      dialog.show();
    } catch (Exception e) {
      error(e);
    }
  }

  void fullEditor(String title, EditText target, boolean json) {
    Dialog dialog = new Dialog(this);
    LinearLayout panel = vertical();
    panel.setPadding(dp(14), dp(14), dp(14), dp(14));
    panel.setBackgroundColor(0xfff5f6fc);
    panel.addView(label(title, 22, INK));
    EditText search = input(panel, "查找", "", false, false),
        replace = input(panel, "替换为", "", false, false);
    EditText editor = new EditText(this);
    editor.setText(target.getText());
    editor.setTypeface(Typeface.MONOSPACE);
    editor.setTextSize(15);
    editor.setGravity(Gravity.TOP);
    editor.setInputType(
        android.text.InputType.TYPE_CLASS_TEXT
            | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    editor.setHorizontallyScrolling(false);
    panel.addView(editor, new LinearLayout.LayoutParams(-1, 0, 1));
    String original = target.getText().toString();
    TextView count = label("", 12, MUTED);
    panel.addView(count);
    Runnable update =
        () ->
            count.setText(
                getString(
                    io.github.astrbot.mobile.R.string.editor_count,
                    editor.length(),
                    editor.getText().toString().split("\n", -1).length,
                    original.equals(editor.getText().toString()) ? "未修改" : "已修改"));
    editor.addTextChangedListener(watcher(update));
    update.run();
    LinearLayout actions = new LinearLayout(this);
    panel.addView(actions);
    smallButton(
        actions,
        "查找",
        () -> {
          String q = search.getText().toString();
          if (q.isEmpty()) return;
          String text = editor.getText().toString();
          int at = text.indexOf(q, editor.getSelectionEnd());
          if (at < 0) at = text.indexOf(q);
          if (at >= 0) {
            editor.requestFocus();
            editor.setSelection(at, at + q.length());
          }
        });
    smallButton(
        actions,
        "替换全部",
        () -> {
          String q = search.getText().toString();
          if (!q.isEmpty())
            editor.setText(editor.getText().toString().replace(q, replace.getText().toString()));
        });
    if (json)
      smallButton(
          actions,
          "格式化",
          () -> {
            try {
              Object obj = new JSONTokener(editor.getText().toString()).nextValue();
              if (!(obj instanceof JSONObject) && !(obj instanceof JSONArray))
                throw new IllegalArgumentException("需要 JSON 对象或数组");
              editor.setText(Helpers.pretty(obj));
            } catch (Exception e) {
              error(e);
            }
          });
    smallButton(actions, "撤销", () -> editor.setText(original));
    button(
        panel,
        "保存到当前表单",
        () -> {
          target.setText(editor.getText());
          dialog.dismiss();
        });
    button(panel, "放弃修改", () -> dialog.dismiss());
    dialog.setContentView(panel);
    dialog.setOnCancelListener(
        d -> {
          if (!original.equals(editor.getText().toString())) toast("未保存的修改已放弃");
        });
    dialog.show();
    if (dialog.getWindow() != null) dialog.getWindow().setLayout(-1, -1);
  }

  private void smallButton(LinearLayout row, String text, Runnable action) {
    Button b = new Button(this);
    b.setText(Appearance.text(text));
    b.setTextSize(11);
    b.setOnClickListener(v -> attempt(action));
    row.addView(b, new LinearLayout.LayoutParams(0, dp(50), 1));
  }

  private void execute(Endpoint e) {
    try {
      JSONObject p = object(paths), q = object(query), b = object(body);
      String resolved = e.resolve(p, q);
      ApiClient activeClient = client;
      byte[] file = upload;
      String field = fileField.getText().toString();
      Runnable run =
          () ->
              async(
                  () -> activeClient.execute(e, p, q, b, file, field),
                  r -> {
                    download = r.body;
                    if (r.contentType.contains("json")) {
                      JSONObject data = new JSONObject(r.text());
                      Helpers.collectSuggestions(data, suggestions, 0);
                      result(Helpers.pretty(Helpers.redact(data)), r.contentType);
                    } else if (r.contentType.startsWith("text/")) result(r.text(), r.contentType);
                    else result("收到 " + r.body.length + " 字节，类型 " + r.contentType, r.contentType);
                  });
      if (e.mutates())
        confirm(
            "执行写入",
            selected.name
                + "\n"
                + e.method
                + " "
                + resolved
                + "\n"
                + Helpers.pretty(Helpers.redact(b)),
            run);
      else run.run();
    } catch (Exception x) {
      error(x);
    }
  }

  private void result(String text, String type) {
    screen("服务器结果", type, () -> operation(editing, null));
    LinearLayout result = card("响应", "展示内容已按字段名称隐藏常见密钥；保存原始响应仍可能包含敏感信息。");
    TextView output =
        label(
            text.length() > 100000 ? text.substring(0, 100000) + "\n（显示已截断，下载可保存完整响应）" : text,
            13,
            INK);
    output.setTypeface(Typeface.MONOSPACE);
    output.setTextIsSelectable(true);
    result.addView(output);
    button(
        result,
        "保存原始响应文件",
        () ->
            confirm(
                "保存原始数据",
                "文件可能含敏感信息，请妥善保管。",
                () -> {
                  Intent i =
                      new Intent(Intent.ACTION_CREATE_DOCUMENT)
                          .setType("application/octet-stream")
                          .putExtra(Intent.EXTRA_TITLE, "astrbot-response.bin");
                  startActivityForResult(i, 11);
                }));
    button(
        result,
        "用响应 data 填充新请求",
        () -> {
          try {
            JSONObject j = new JSONObject(new String(download, StandardCharsets.UTF_8));
            Object data = j.opt("data");
            if (!(data instanceof JSONObject))
              throw new IllegalArgumentException("data 不是 JSON 对象");
            JSONObject preset =
                new JSONObject()
                    .put("body", data)
                    .put("pathParams", new JSONObject())
                    .put("query", new JSONObject());
            operation(editing, preset);
          } catch (Exception e) {
            error(e);
          }
        });
  }

  private void textViewer(String title, String text) {
    EditText e = new EditText(this);
    e.setText(text);
    e.setTypeface(Typeface.MONOSPACE);
    fullEditor(title, e, true);
  }

  private void savePreset(Endpoint e) {
    LinearLayout panel = vertical();
    panel.setPadding(dp(18), 0, dp(18), 0);
    EditText title = input(panel, "预设名称", e.title, false, false);
    CheckBox secrets = new CheckBox(this);
    secrets.setText(Appearance.text("保留请求中的密钥（加密存于本机）"));
    panel.addView(secrets);
    new LocalizedDialogBuilder(this)
        .setTitle("保存请求预设")
        .setView(panel)
        .setNegativeButton("取消", null)
        .setPositiveButton(
            "保存",
            (d, w) -> {
              try {
                JSONObject p =
                    Helpers.preset(
                        e, object(paths), object(query), object(body), secrets.isChecked());
                p.put("name", title.getText().toString()).put("source", selected.name);
                list("presets").put(p);
                persist();
                toast("已保存，可在预设库选择目标实例后加载");
              } catch (Exception x) {
                error(x);
              }
            })
        .show();
  }

  private void presets() {
    screen(
        "预设库",
        "保存参数、提示词和 MCP 配置；传递前逐项复核，绝不自动批量写入",
        selected == null ? this::home : this::workspace);
    JSONArray presets = list("presets");
    for (int i = 0; i < presets.length(); i++) {
      JSONObject p = presets.optJSONObject(i);
      int index = i;
      LinearLayout c =
          card(
              p.optString("name"),
              p.optString("source") + " · " + p.optString("method") + " " + p.optString("path"));
      button(
          c,
          "加载到当前实例",
          () -> {
            if (client == null) {
              toast("请先连接目标实例，再打开预设库");
              return;
            }
            Endpoint match = null;
            for (Endpoint e : endpoints)
              if (e.path.equals(p.optString("path")) && e.method.equals(p.optString("method"))) {
                match = e;
                break;
              }
            if (match == null) {
              toast("目标版本没有同一路径，请根据目标接口手动迁移参数");
              return;
            }
            operation(match, p);
          });
      button(
          c,
          "复制并编辑预设",
          () -> {
            try {
              JSONObject copy = new JSONObject(p.toString());
              EditText editor = new EditText(this);
              editor.setText(copy.toString(2));
              new LocalizedDialogBuilder(this)
                  .setTitle("复制预设")
                  .setView(editor)
                  .setNegativeButton("取消", null)
                  .setPositiveButton(
                      "保存副本",
                      (d, w) -> {
                        try {
                          JSONObject n = new JSONObject(editor.getText().toString());
                          n.put("name", n.optString("name") + " 副本");
                          presets.put(n);
                          persist();
                          presets();
                        } catch (Exception e) {
                          error(e);
                        }
                      })
                  .show();
            } catch (Exception e) {
              error(e);
            }
          });
      button(
          c,
          "删除",
          () ->
              confirm(
                  "删除预设",
                  p.optString("name"),
                  () -> {
                    try {
                      presets.remove(index);
                      persist();
                      presets();
                    } catch (Exception e) {
                      error(e);
                    }
                  }));
    }
    if (presets.length() == 0) card("暂无预设", "在任意请求页点击“保存为预设”。切换到另一连接后即可加载。");
  }

  private void extensions() {
    screen(
        "客户端插件",
        "安装声明式 JSON 扩展，增加原生 API 操作；不运行下载的 Android 代码",
        selected == null ? this::home : this::workspace);
    LinearLayout intro = card("扩展管理", "插件只声明本站 /api/ 路径、方法和参数。执行时仍需确认写入。");
    button(
        intro,
        "导入插件 JSON",
        () -> {
          Intent i =
              new Intent(Intent.ACTION_OPEN_DOCUMENT)
                  .setType("application/json")
                  .addCategory(Intent.CATEGORY_OPENABLE);
          startActivityForResult(i, 12);
        });
    JSONArray extensions = list("extensions");
    for (int i = 0; i < extensions.length(); i++) {
      JSONObject ext = extensions.optJSONObject(i);
      int index = i;
      LinearLayout c =
          card(
              ext.optString("name"),
              "API " + ext.optInt("apiVersion") + " · " + ext.optString("description"));
      JSONArray ops = ext.optJSONArray("operations");
      if (ops != null)
        for (int j = 0; j < ops.length(); j++) {
          JSONObject op = ops.optJSONObject(j);
          button(
              c,
              op.optString("title", op.optString("path")),
              () -> {
                if (client == null) {
                  toast("请先连接实例");
                  return;
                }
                try {
                  JSONObject def =
                      new JSONObject()
                          .put("summary", op.optString("title"))
                          .put("tags", new JSONArray().put("客户端插件"))
                          .put("description", op.optString("description"));
                  Endpoint e =
                      new Endpoint(op.getString("path"), op.optString("method", "GET"), def);
                  operation(e, op);
                } catch (Exception e) {
                  error(e);
                }
              });
        }
      button(
          c,
          "卸载",
          () ->
              confirm(
                  "卸载插件",
                  ext.optString("name"),
                  () -> {
                    try {
                      extensions.remove(index);
                      persist();
                      extensions();
                    } catch (Exception e) {
                      error(e);
                    }
                  }));
    }
  }

  private void importExtension(byte[] bytes) throws Exception {
    JSONObject ext = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
    if (ext.optInt("apiVersion") != 1 || ext.optString("name").isBlank())
      throw new IllegalArgumentException("插件需要 apiVersion:1 和 name");
    JSONArray ops = ext.getJSONArray("operations");
    if (ops.length() > 50) throw new IllegalArgumentException("插件最多 50 个操作");
    for (int i = 0; i < ops.length(); i++) {
      JSONObject op = ops.getJSONObject(i);
      String path = op.getString("path");
      if (!path.startsWith("/api/")
          || path.contains("..")
          || path.contains("\\")
          || path.contains("?")) throw new IllegalArgumentException("插件路径必须位于本站 /api/");
      if (!List.of("GET", "POST", "PUT", "PATCH", "DELETE").contains(op.optString("method", "GET")))
        throw new IllegalArgumentException("无效方法");
    }
    confirm(
        "安装客户端插件",
        ext.optString("name") + "\n" + ext.optString("description") + "\n操作将使用你当前实例的权限。",
        () -> {
          try {
            list("extensions").put(ext);
            persist();
            extensions();
          } catch (Exception e) {
            error(e);
          }
        });
  }

  private void pluginEntry() {
    screen("插件自定义页面", "选择插件与页面", this::workspace);
    LinearLayout c = card("页面入口", "也可以从插件管理列表直接打开已声明的页面。");
    EditText plugin = input(c, "插件名称", "", false, false), view = input(c, "页面名称", "", false, false);
    primaryButton(
        c, "打开页面", () -> openPluginPage(plugin.getText().toString(), view.getText().toString()));
  }

  private void openPluginPage(String plugin, String view) {
    try {
      String path =
          client.modern
              ? "/api/v1/plugins/" + Endpoint.encode(plugin) + "/views/" + Endpoint.encode(view)
              : "/api/plugin/page/entry?name="
                  + Endpoint.encode(plugin)
                  + "&page="
                  + Endpoint.encode(view);
      async(
          () -> new ManagementApi(client).pluginPage(plugin, view),
          j -> {
            JSONObject data = j.optJSONObject("data");
            if (data == null) throw new IllegalArgumentException("缺少页面入口");
            String entry = data.optString("content_path", data.optString("url"));
            if (entry.isEmpty()) throw new IllegalArgumentException("此版本没有支持的页面入口，请使用 WebUI");
            String base = Connection.normalizeUrl(selected.url);
            String full =
                entry.startsWith("/")
                    ? base + entry
                    : java.net.URI.create(base + "/").resolve(entry).toString();
            if (!PluginPageActivity.allowed(base, full))
              throw new IllegalArgumentException("页面地址超出当前实例");
            startActivity(
                new Intent(this, PluginPageActivity.class)
                    .putExtra("base", base)
                    .putExtra("url", full)
                    .putExtra("plugin", plugin)
                    .putExtra("session", PluginSessions.add(client)));
          });
    } catch (Exception e) {
      error(e);
    }
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (result != RESULT_OK || data == null || data.getData() == null) return;
    Uri uri = data.getData();
    try {
      if (request == 10) {
        upload =
            HttpTransport.readLimited(getContentResolver().openInputStream(uri), 8 * 1024 * 1024);
        if (uploadLabel != null)
          uploadLabel.setText(
              getString(io.github.astrbot.mobile.R.string.upload_selected, upload.length));
      } else if (request == 11 && download != null) {
        try (java.io.OutputStream out = getContentResolver().openOutputStream(uri)) {
          if (out == null) throw new java.io.IOException("不能写入文件");
          out.write(download);
        }
        toast("已保存");
      } else if (request == 12)
        importExtension(
            HttpTransport.readLimited(getContentResolver().openInputStream(uri), 256 * 1024));
    } catch (Exception e) {
      error(e);
    }
  }

  private void goBack() {
    if (busy) {
      confirm(
          "取消当前请求",
          "取消后请刷新确认服务器状态，写入可能已经完成。",
          () -> {
            generation++;
            if (transport != null) transport.cancel();
            if (backAction != null) backAction.run();
          });
    } else if (backAction != null) backAction.run();
    else finish();
  }

  @SuppressWarnings("deprecation")
  @Override
  public void onBackPressed() {
    goBack();
  }

  @Override
  protected void onDestroy() {
    generation++;
    if (updater != null) updater.close();
    if (transport != null) transport.cancel();
    worker.shutdownNow();
    super.onDestroy();
  }
}
