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
  private LinearLayout page, content, toolbar;
  private TextView subtitle;
  private Vault vault;
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
  private static final int ACCENT = 0xff5769dd, INK = 0xff202641, MUTED = 0xff747b95;

  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    getWindow()
        .setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE);
    vault = new Vault(this);
    try {
      state = vault.load();
      home();
    } catch (Exception e) {
      new AlertDialog.Builder(this)
          .setTitle("无法读取本地配置")
          .setMessage("密钥或存储异常。配置未被覆盖。可在系统设置清除应用数据后重新配置。")
          .setPositiveButton("关闭", (d, w) -> finish())
          .setCancelable(false)
          .show();
    }
  }

  private int dp(int n) {
    return (int) (n * getResources().getDisplayMetrics().density + .5f);
  }

  private LinearLayout vertical() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(LinearLayout.VERTICAL);
    return l;
  }

  private TextView label(String s, int size, int color) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setPadding(0, dp(5), 0, dp(5));
    return t;
  }

  private GradientDrawable background(int color, int radius) {
    GradientDrawable b = new GradientDrawable();
    b.setColor(color);
    b.setCornerRadius(dp(radius));
    return b;
  }

  private void screen(String title, String hint, Runnable back) {
    generation++;
    backAction = back;
    page = vertical();
    page.setBackgroundColor(0xfff5f6fc);
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
      TextView b = label("‹  返回", 15, ACCENT);
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

  private LinearLayout card(String title, String desc) {
    LinearLayout c = vertical();
    c.setPadding(dp(18), dp(15), dp(18), dp(15));
    c.setBackground(background(Color.WHITE, 20));
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

  private Button button(LinearLayout parent, String title, Runnable action) {
    Button b = new Button(this);
    b.setText(title);
    b.setTextColor(ACCENT);
    b.setAllCaps(false);
    b.setOnClickListener(
        v -> {
          if (!busy) attempt(action);
          else toast("正在处理，请稍候或取消");
        });
    parent.addView(b, new LinearLayout.LayoutParams(-1, dp(50)));
    return b;
  }

  private EditText input(
      LinearLayout parent, String hint, String value, boolean secret, boolean multiline) {
    parent.addView(label(hint, 13, MUTED));
    AutoCompleteTextView e = new AutoCompleteTextView(this);
    e.setText(value);
    e.setTextSize(15);
    e.setTextColor(INK);
    e.setSingleLine(!multiline);
    e.setPadding(dp(10), dp(10), dp(10), dp(10));
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

  private void toast(String text) {
    Toast.makeText(this, text, Toast.LENGTH_LONG).show();
  }

  private void attempt(Runnable action) {
    try {
      action.run();
    } catch (Exception e) {
      error(e);
    }
  }

  private void error(Throwable e) {
    if (!isFinishing())
      new AlertDialog.Builder(this)
          .setTitle("操作未完成")
          .setMessage(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())
          .setPositiveButton("知道了", null)
          .show();
  }

  private interface Task<T> {
    T run() throws Exception;
  }

  private interface Done<T> {
    void accept(T value) throws Exception;
  }

  private <T> void async(Task<T> task, Done<T> done) {
    if (busy) {
      toast("已有任务正在处理");
      return;
    }
    busy = true;
    int view = generation;
    subtitle.setText("正在连接服务器…");
    worker.submit(
        () -> {
          try {
            T result = task.run();
            runOnUiThread(
                () -> {
                  busy = false;
                  if (view == generation && !isFinishing())
                    try {
                      subtitle.setText("操作完成");
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
                    subtitle.setText("操作未完成");
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

  private void confirm(String title, String message, Runnable yes) {
    new AlertDialog.Builder(this)
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
    screen("AstrBot Mobile", "你的机器人，随身掌控", null);
    LinearLayout intro = card("连接工作空间", "在多个实例和分组间切换。密码与预设在本机加密保存。");
    button(intro, "＋ 添加连接", () -> connectionForm(null));
    LinearLayout search = card("实例", "按名称、分组或地址筛选");
    EditText f = input(search, "搜索", filter, false, false);
    LinearLayout holder = vertical();
    content.addView(holder);
    renderConnections(holder, filter);
    f.addTextChangedListener(
        watcher(
            () -> {
              filter = f.getText().toString();
              renderConnections(holder, filter);
            }));
    LinearLayout tools = card("工作台", "可复用预设与原生客户端扩展");
    button(tools, "预设库", this::presets);
    button(tools, "客户端插件", this::extensions);
    CheckBox assist = new CheckBox(this);
    assist.setText("输入辅助与自动补全（当前会话）");
    assist.setChecked(autofill);
    assist.setOnCheckedChangeListener((b, c) -> autofill = c);
    tools.addView(assist);
    button(
        tools,
        "使用指南",
        () ->
            startActivity(
                new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://z2bguoguo.github.io/AstrBotMobileUI/"))));
  }

  private TextWatcher watcher(Runnable r) {
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
      LinearLayout row = vertical();
      row.setPadding(dp(16), dp(12), dp(16), dp(12));
      row.setBackground(background(Color.WHITE, 18));
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
      lp.bottomMargin = dp(10);
      holder.addView(row, lp);
      row.addView(label(c.name, 20, INK));
      row.addView(label(c.url, 12, MUTED));
      button(row, "连接", () -> connect(c));
      button(row, "编辑 · 复制 · 移动分组", () -> connectionForm(c));
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
    Spinner mode = new Spinner(this);
    mode.setAdapter(
        new ArrayAdapter<>(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            new String[] {"原文密码（新版默认）", "MD5 密码（部分旧版）"}));
    mode.setSelection(c.passwordMode.equals("md5") ? 1 : 0);
    form.addView(mode);
    form.addView(label("HTTP 会明文传输凭据，建议使用 HTTPS 或可信局域网。API Key 权限不足时可改用 WebUI 账户。", 13, MUTED));
    button(
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
            n.apiKey = key.getText().toString();
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
    screen(
        selected.name,
        selected.group
            + " · "
            + (client.modern ? "OpenAPI 自适应" : "旧版 WebUI 接口")
            + " · "
            + endpoints.size()
            + " 项操作",
        this::home);
    LinearLayout quick = card("工作空间", "原生参数页覆盖已发现的 HTTP 接口；自定义插件页面通过独立 HTML 视图打开。");
    button(quick, "概览", () -> quickRead("/api/v1/stats", "/api/stat/get"));
    button(quick, "插件自定义 HTML 页面", this::pluginEntry);
    button(quick, "预设与跨实例配置传递", this::presets);
    button(quick, "客户端插件", this::extensions);
    Set<String> groups = new TreeSet<>();
    for (Endpoint e : endpoints) groups.add(e.category);
    LinearLayout modules = card("功能模块", "按服务器当前契约生成；找不到的操作可使用手动接口页。");
    for (String g : groups) button(modules, CATEGORIES.getOrDefault(g, g), () -> operations(g));
    button(modules, "全部操作 / 搜索", () -> operations(null));
    button(modules, "手动 API 请求", this::manualEndpoint);
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
        button(form, "查看参数说明", () -> textViewer("参数说明", params.toString()));
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
          new AlertDialog.Builder(this)
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

  private void fullEditor(String title, EditText target, boolean json) {
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
    b.setText(text);
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
    secrets.setText("保留请求中的密钥（加密存于本机）");
    panel.addView(secrets);
    new AlertDialog.Builder(this)
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
              new AlertDialog.Builder(this)
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
    screen("插件 HTML 页面", "使用 AstrBot 颁发的插件页面令牌，不把账户密码注入网页", this::workspace);
    LinearLayout c = card("页面入口", "插件名和 View 名见插件详情返回的 pages / views；不同版本提供的入口不同。");
    EditText plugin = input(c, "插件名", "", false, false),
        view = input(c, "View / 页面名", "", false, false);
    button(
        c,
        "打开插件页面",
        () -> {
          try {
            String path =
                client.modern
                    ? "/api/v1/plugins/"
                        + Endpoint.encode(plugin.getText().toString())
                        + "/views/"
                        + Endpoint.encode(view.getText().toString())
                    : "/api/plugin/page/entry?name="
                        + Endpoint.encode(plugin.getText().toString())
                        + "&page="
                        + Endpoint.encode(view.getText().toString());
            async(
                () -> ApiClient.json(client.request(path, "GET", null, null)),
                j -> {
                  JSONObject data = j.optJSONObject("data");
                  if (data == null) throw new IllegalArgumentException("缺少页面入口");
                  String entry = data.optString("content_path", data.optString("url"));
                  if (entry.isEmpty())
                    throw new IllegalArgumentException("此版本没有支持的令牌页面入口，请使用 WebUI");
                  String base = Connection.normalizeUrl(selected.url);
                  String full =
                      entry.startsWith("/")
                          ? base + entry
                          : java.net.URI.create(base + "/").resolve(entry).toString();
                  if (!PluginPageActivity.allowed(base, full))
                    throw new IllegalArgumentException("已拒绝跨实例页面入口");
                  startActivity(
                      new Intent(this, PluginPageActivity.class)
                          .putExtra("base", base)
                          .putExtra("url", full)
                          .putExtra("plugin", plugin.getText().toString())
                          .putExtra("session", PluginSessions.add(client)));
                });
          } catch (Exception e) {
            error(e);
          }
        });
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
    if (transport != null) transport.cancel();
    worker.shutdownNow();
    super.onDestroy();
  }
}
