package io.github.astrbot.mobile.ui;

import android.app.*;
import android.content.*;
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
  private final Map<String, ReadWatch> watches = new LinkedHashMap<>();
  private final Map<String, JSONObject> watchPlans = new LinkedHashMap<>();
  private NativeViews scriptView;
  private boolean watchPaused;
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
    return (LinearLayout) NativeViews.template(this, "column", new JSONObject(), e -> {}).root;
  }

  TextView label(String s, int size, int color) {
    return (TextView)
        NativeViews.template(
                this, "label", CoreRuntime.args("text", s, "size", size, "color", color), e -> {})
            .root;
  }

  GradientDrawable background(int color, int radius) {
    GradientDrawable b = new GradientDrawable();
    b.setColor(Appearance.color(color));
    b.setCornerRadius(dp(radius));
    return b;
  }

  void screen(String title, String hint, Runnable back) {
    stopWatches(false);
    scriptView = null;
    generation++;
    backAction = back;
    NativeViews ui =
        NativeViews.template(
            this,
            "screen",
            CoreRuntime.args("title", title, "hint", hint, "back", back != null),
            e -> goBack());
    page = (LinearLayout) ui.refs.get("page");
    toolbar = (LinearLayout) ui.refs.get("toolbar");
    content = (LinearLayout) ui.refs.get("content");
    subtitle = (TextView) ui.refs.get("subtitle");
    page.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          v.setPadding(
              insets.getSystemWindowInsetLeft(),
              insets.getSystemWindowInsetTop(),
              insets.getSystemWindowInsetRight(),
              insets.getSystemWindowInsetBottom());
          return insets;
        });
    setContentView(page);
    page.requestApplyInsets();
  }

  LinearLayout card(String title, String desc) {
    NativeViews ui =
        NativeViews.template(this, "card", CoreRuntime.args("title", title, "desc", desc), e -> {});
    ui.attach(content, ui.root, ui.rootNode);
    return (LinearLayout) ui.root;
  }

  Button button(LinearLayout parent, String title, Runnable action) {
    return scriptedButton(parent, title, action, false);
  }

  Button primaryButton(LinearLayout parent, String title, Runnable action) {
    return scriptedButton(parent, title, action, true);
  }

  LinearLayout group(LinearLayout parent) {
    NativeViews ui = NativeViews.template(this, "group", new JSONObject(), e -> {});
    ui.attach(parent, ui.root, ui.rootNode);
    return (LinearLayout) ui.root;
  }

  void navRow(LinearLayout parent, String title, String detail, String icon, Runnable action) {
    NativeViews ui =
        NativeViews.template(
            this,
            "nav",
            CoreRuntime.args(
                "title", title, "detail", detail, "icon", icon, "active", action != null),
            e -> {
              if (!busy) attempt(action);
              else toast("正在处理，请稍候");
            });
    ui.attach(parent, ui.root, ui.rootNode);
  }

  private Button scriptedButton(
      LinearLayout parent, String title, Runnable action, boolean primary) {
    NativeViews ui =
        NativeViews.template(
            this,
            "button",
            CoreRuntime.args("title", title, "primary", primary),
            e -> {
              if (!busy) attempt(action);
              else toast("正在处理，请稍候或取消");
            });
    ui.attach(parent, ui.root, ui.rootNode);
    return (Button) ui.root;
  }

  void textAction(LinearLayout parent, String title, Runnable action) {
    NativeViews ui =
        NativeViews.template(
            this, "textAction", CoreRuntime.args("title", title), e -> attempt(action));
    ui.attach(parent, ui.root, ui.rootNode);
  }

  void editLongText(
      String title, String value, boolean json, java.util.function.Consumer<String> applied) {
    EditText target = new EditText(this);
    target.setText(value);
    target.addTextChangedListener(watcher(() -> applied.accept(target.getText().toString())));
    fullEditor(title, target, json);
  }

  private void bottomNav(boolean connected, String current) {
    Runnable[] actions =
        connected
            ? new Runnable[] {this::overview, this::workspace, this::advancedTools}
            : new Runnable[] {this::home, this::presets, this::appSettings};
    NativeViews ui =
        NativeViews.template(
            this,
            "bottomNav",
            CoreRuntime.args("connected", connected, "current", current),
            e -> {
              int i = e.optInt("index", -1);
              if (i >= 0 && i < actions.length && !busy) attempt(actions[i]);
            });
    ui.attach(page, ui.root, ui.rootNode);
  }

  EditText input(
      LinearLayout parent, String hint, String value, boolean secret, boolean multiline) {
    NativeViews ui =
        NativeViews.template(
            this,
            "input",
            CoreRuntime.args(
                "hint", hint, "value", value, "secret", secret, "multiline", multiline),
            e -> {});
    AutoCompleteTextView input = (AutoCompleteTextView) ui.refs.get("input");
    input.setTag(ui.refs.get("caption"));
    if (autofill && !secret)
      input.setAdapter(
          new ArrayAdapter<>(
              this, android.R.layout.simple_dropdown_item_1line, new ArrayList<>(suggestions)));
    ui.attach(parent, ui.root, ui.rootNode);
    return input;
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
    NativeViews ui =
        NativeViews.template(
            this, "home", CoreRuntime.args("filter", filter), e -> connectionForm(null));
    ui.attach(content, ui.root, ui.rootNode);
    EditText search = (EditText) ui.refs.get("filter");
    LinearLayout holder = (LinearLayout) ui.refs.get("connections");
    renderConnections(holder, filter);
    search.addTextChangedListener(
        watcher(
            () -> {
              filter = search.getText().toString();
              renderConnections(holder, filter);
            }));
    bottomNav(false, "连接");
  }

  private void appSettings() {
    screen("应用设置", "管理本机偏好与诊断", this::home);
    NativeViews ui =
        NativeViews.template(
            this,
            "appSettings",
            CoreRuntime.args("autofill", autofill),
            event -> {
              if (event.optString("kind").equals("autofill")) {
                try {
                  autofill = event.optBoolean("checked");
                  state.put("autofill", autofill);
                  persist();
                } catch (Exception e) {
                  error(e);
                }
              } else scriptNavigation(event);
            });
    ui.attach(content, ui.root, ui.rootNode);
    Appearance.settings(this, (LinearLayout) ui.refs.get("appearance"));
    if (updater != null) updater.settings((LinearLayout) ui.refs.get("updates"));
    bottomNav(false, "设置");
  }

  void refreshUpdateSettings() {
    appSettings();
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (updater != null) updater.resume();
    watchPaused = false;
    for (JSONObject plan : new ArrayList<>(watchPlans.values())) scriptCommand(plan);
  }

  @Override
  protected void onStop() {
    watchPaused = true;
    stopWatches(true);
    super.onStop();
  }

  private void stopWatches(boolean retain) {
    for (Map.Entry<String, ReadWatch> entry : watches.entrySet()) {
      JSONObject plan = watchPlans.get(entry.getKey());
      if (retain && plan != null)
        try {
          plan.put("lastEventId", entry.getValue().lastEventId());
        } catch (JSONException ignored) {
        }
      entry.getValue().close();
    }
    watches.clear();
    if (!retain) watchPlans.clear();
  }

  private void startWatch(JSONObject command) throws JSONException {
    if (client == null) throw new IllegalStateException("请先连接实例");
    String id = command.optString("id", "read");
    ReadWatch previous = watches.remove(id);
    if (previous != null) previous.close();
    if (watchPlans.size() >= 4 && !watchPlans.containsKey(id))
      throw new IllegalArgumentException("Too many watches");
    watchPlans.put(id, new JSONObject(command.toString()));
    if (watchPaused) return;
    final int pageGeneration = generation;
    final ApiClient owner = client;
    final ReadWatch[] holder = new ReadWatch[1];
    ReadWatch watch =
        new ReadWatch(
            owner,
            command,
            event ->
                runOnUiThread(
                    () -> {
                      if (watchPaused
                          || generation != pageGeneration
                          || client != owner
                          || watches.get(id) != holder[0]) return;
                      try {
                        scriptCommand(
                            CoreRuntime.object(
                                command.getString("handler"),
                                CoreRuntime.args(
                                    "data",
                                    event,
                                    "event",
                                    command,
                                    "language",
                                    Appearance.language())));
                        if (event.optString("kind").equals("data")
                            && !command.optString("mode").equals("sse")
                            && command.optInt("intervalMs") == 0
                            && watches.get(id) == holder[0]) {
                          watches.remove(id);
                          watchPlans.remove(id);
                          holder[0].close();
                        }
                      } catch (Exception e) {
                        error(e);
                      }
                    }));
    holder[0] = watch;
    watches.put(id, watch);
    watch.start();
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
    NativeViews ui =
        NativeViews.template(
            this,
            "connections",
            CoreRuntime.args("connections", list("connections"), "filter", text),
            event -> {
              for (int i = 0; i < list("connections").length(); i++) {
                Connection c = Connection.fromJson(list("connections").optJSONObject(i));
                if (!c.id.equals(event.optString("id"))) continue;
                if (event.optString("kind").equals("connect")) connect(c);
                else connectionForm(c);
                break;
              }
            });
    ui.attach(holder, ui.root, ui.rootNode);
  }

  private void connectionForm(Connection original) {
    screen(
        original == null ? "新建连接" : "连接设置",
        "可填写反向代理子路径，例如 https://bot.example.com/astrbot",
        this::home);
    try {
      Connection c = original == null ? new Connection() : original;
      NativeViews[] holder = new NativeViews[1];
      holder[0] =
          NativeViews.template(
              this,
              "connection",
              CoreRuntime.args("connection", c.toJson(), "isNew", original == null),
              event -> {
                try {
                  String kind = event.optString("kind");
                  if (kind.equals("auth")) {
                    holder[0].apply(CoreRuntime.object("ui.connectionAuth", event));
                    return;
                  }
                  Runnable commit =
                      () -> {
                        try {
                          JSONObject connection =
                              kind.equals("save")
                                  ? CoreRuntime.object(
                                      "ui.connectionValue",
                                      CoreRuntime.args(
                                          "connection", c.toJson(), "fields", holder[0].fields()))
                                  : c.toJson();
                          if (kind.equals("save"))
                            connection.put(
                                "url", Connection.normalizeUrl(connection.getString("url")));
                          JSONObject next =
                              CoreRuntime.object(
                                  "ui.connectionCommit",
                                  CoreRuntime.args(
                                      "state",
                                      state,
                                      "connection",
                                      connection,
                                      "operation",
                                      kind,
                                      "newId",
                                      java.util.UUID.randomUUID().toString()));
                          vault.save(next);
                          state = next;
                          home();
                        } catch (Exception e) {
                          error(e);
                        }
                      };
                  if (kind.equals("delete")) confirm("删除连接", c.name + " 的本地密码也会删除。", commit);
                  else commit.run();
                } catch (Exception e) {
                  error(e);
                }
              });
      holder[0].attach(content, holder[0].root, holder[0].rootNode);
    } catch (Exception e) {
      error(e);
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
    scriptMenu("workspace");
    bottomNav(true, "管理");
  }

  private void advancedTools() {
    screen("工具", "预设、扩展与高级诊断", this::workspace);
    scriptMenu("advanced");
    bottomNav(true, "工具");
  }

  private void scriptMenu(String name) {
    NativeViews ui = NativeViews.template(this, name, new JSONObject(), this::scriptNavigation);
    ui.attach(content, ui.root, ui.rootNode);
  }

  private void scriptNavigation(JSONObject event) {
    if (busy) {
      toast("正在处理，请稍候");
      return;
    }
    attempt(
        () -> {
          switch (event.optString("kind")) {
            case "settings":
              openConfiguration(event.optString("id"), "通用设置");
              break;
            case "profiles":
              profiles();
              break;
            case "resource":
              resources(ManagementApi.Resource.valueOf(event.optString("resource")));
              break;
            case "presets":
              presets();
              break;
            case "extensions":
              extensions();
              break;
            case "pluginEntry":
              pluginEntry();
              break;
            case "operations":
              operations(null);
              break;
            case "manual":
              manualEndpoint();
              break;
            default:
              scriptCommand(event);
              break;
          }
        });
  }

  private void scriptCommand(JSONObject command) {
    try {
      switch (command.optString("kind")) {
        case "batch":
          JSONArray commands = command.getJSONArray("commands");
          if (commands.length() > 12) throw new IllegalArgumentException("Command batch too large");
          for (int i = 0; i < commands.length(); i++) scriptCommand(commands.getJSONObject(i));
          break;
        case "none":
          break;
        case "viewer":
          textViewer(
              command.optString("title"), Helpers.pretty(Helpers.redact(command.get("data"))));
          break;
        case "workspace":
          workspace();
          break;
        case "watch":
          startWatch(command);
          break;
        case "cancelWatch":
          String watchId = command.getString("id");
          ReadWatch watch = watches.remove(watchId);
          if (watch != null) watch.close();
          watchPlans.remove(watchId);
          break;
        case "render":
          if (scriptView == null) break;
          View target = scriptView.refs.get(command.getString("slot"));
          if (!(target instanceof ViewGroup))
            throw new IllegalArgumentException("Unknown script slot");
          ViewGroup slot = (ViewGroup) target;
          slot.removeAllViews();
          NativeViews fragment = new NativeViews(this, this::scriptNavigation);
          fragment.build(command.getJSONObject("tree"), 0);
          fragment.attach(slot, fragment.root, fragment.rootNode);
          break;
        case "screen":
          screen(
              command.optString("title"),
              command.optString("hint"),
              command.has("back")
                  ? () -> scriptCommand(command.optJSONObject("back"))
                  : this::workspace);
          scriptView = new NativeViews(this, this::scriptNavigation);
          scriptView.build(command.getJSONObject("tree"), 0);
          scriptView.attach(content, scriptView.root, scriptView.rootNode);
          break;
        case "request":
          if (client == null) throw new IllegalStateException("请先连接实例");
          String path = command.getString("path"), method = command.optString("method", "GET");
          JSONObject body = command.optJSONObject("body");
          ApiClient requestOwner = client;
          Runnable run =
              () ->
                  async(
                      () ->
                          ApiClient.json(
                              requestOwner.request(
                                  path,
                                  method,
                                  body == null
                                      ? null
                                      : body.toString().getBytes(StandardCharsets.UTF_8),
                                  body == null ? null : "application/json")),
                      result -> {
                        JSONObject response =
                            CoreRuntime.object(
                                command.optString("handler", "ui.response"),
                                CoreRuntime.args(
                                    "data",
                                    result,
                                    "event",
                                    command,
                                    "language",
                                    Appearance.language()));
                        scriptCommand(response);
                      });
          if (!method.equals("GET") && !method.equals("HEAD"))
            confirm(
                command.optString("confirmTitle", "执行写入"),
                command.optString("confirmMessage", method + " " + path),
                run);
          else run.run();
          break;
        case "event":
          scriptCommand(
              CoreRuntime.object(
                  command.getString("handler"),
                  CoreRuntime.args(
                      "data",
                      command,
                      "state",
                      state,
                      "session",
                      CoreRuntime.args(
                          "connectionId",
                          selected == null ? "" : selected.id,
                          "modern",
                          client != null && client.modern,
                          "name",
                          selected == null ? "" : selected.name,
                          "group",
                          selected == null ? "" : selected.group),
                      "language",
                      Appearance.language())));
          break;
        case "preferences":
          String namespace = command.optString("namespace");
          if (!namespace.equals("appearance") && !namespace.equals("updates"))
            throw new SecurityException("Unsupported preference namespace");
          android.content.SharedPreferences.Editor preferences =
              getSharedPreferences(namespace, 0).edit();
          JSONObject values = command.getJSONObject("values");
          for (Iterator<String> it = values.keys(); it.hasNext(); ) {
            String key = it.next();
            Object value = values.get(key);
            if (value instanceof Boolean) preferences.putBoolean(key, (Boolean) value);
            else if (value instanceof Number) preferences.putInt(key, ((Number) value).intValue());
            else if (value instanceof String) preferences.putString(key, (String) value);
            else throw new IllegalArgumentException("Preference must be scalar");
          }
          preferences.apply();
          if (command.optBoolean("reload")) recreate();
          break;
        case "saveState":
          JSONObject next = command.getJSONObject("state");
          for (String key : new String[] {"connections", "presets", "extensions"})
            if (next.optJSONArray(key) == null)
              throw new IllegalArgumentException("Incomplete encrypted state");
          vault.save(next);
          state = next;
          if (command.optJSONObject("next") != null) scriptCommand(command.getJSONObject("next"));
          break;
        case "openPlugin":
          openPluginPage(command.getString("plugin"), command.getString("view"));
          break;
        case "openUrl":
          Uri uri = Uri.parse(command.getString("url"));
          if (!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme()))
            throw new SecurityException("Only HTTP(S) links are supported");
          startActivity(new Intent(Intent.ACTION_VIEW, uri));
          break;
        case "toast":
          toast(command.optString("text"));
          break;
        default:
          throw new IllegalArgumentException("Unsupported script command");
      }
    } catch (Exception e) {
      error(e);
    }
  }

  private void overview() {
    scriptCommand(CoreRuntime.args("kind", "event", "handler", "monitor.open", "page", "status"));
    bottomNav(true, "总览");
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
    String original = target.getText().toString();
    NativeViews[] views = new NativeViews[1];
    views[0] =
        NativeViews.template(
            this,
            "editor",
            CoreRuntime.args("title", title, "value", original, "json", json),
            event -> {
              try {
                EditText editor = (EditText) views[0].refs.get("editor");
                event.put("cursor", editor.getSelectionEnd());
                event.put("original", original);
                JSONObject result = CoreRuntime.object("ui.editorAction", event);
                switch (result.optString("kind")) {
                  case "selection":
                    if (result.optInt("start", -1) >= 0) {
                      editor.requestFocus();
                      editor.setSelection(result.getInt("start"), result.getInt("end"));
                    }
                    break;
                  case "text":
                    editor.setText(result.optString("text"));
                    break;
                  case "formatJson":
                    Object value = new JSONTokener(editor.getText().toString()).nextValue();
                    if (!(value instanceof JSONObject) && !(value instanceof JSONArray))
                      throw new IllegalArgumentException("需要 JSON 对象或数组");
                    editor.setText(Helpers.pretty(value));
                    break;
                  case "apply":
                    target.setText(result.optString("text"));
                    dialog.dismiss();
                    break;
                  case "dismiss":
                    dialog.dismiss();
                    break;
                  default:
                    throw new IllegalArgumentException("Unknown editor event");
                }
              } catch (Exception e) {
                error(e);
              }
            });
    EditText editor = (EditText) views[0].refs.get("editor");
    TextView count = (TextView) views[0].refs.get("count");
    Runnable update =
        () ->
            count.setText(
                CoreRuntime.text(
                    "ui.editorCount",
                    CoreRuntime.args(
                        "value",
                        editor.getText().toString(),
                        "original",
                        original,
                        "language",
                        Appearance.language())));
    editor.addTextChangedListener(watcher(update));
    update.run();
    dialog.setContentView(views[0].root);
    dialog.setOnCancelListener(
        d -> {
          if (!original.equals(editor.getText().toString())) toast("未保存的修改已放弃");
        });
    dialog.show();
    if (dialog.getWindow() != null) dialog.getWindow().setLayout(-1, -1);
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
    stopWatches(false);
    if (updater != null) updater.close();
    if (transport != null) transport.cancel();
    worker.shutdownNow();
    super.onDestroy();
  }
}
