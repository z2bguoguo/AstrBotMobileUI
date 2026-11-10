package io.github.astrbot.mobile.ui;

import android.app.AlertDialog;
import android.text.InputType;
import android.widget.*;
import io.github.astrbot.mobile.core.*;
import java.util.*;
import org.json.*;

/** Lazy native settings pages over a detached draft, with no network side effects before Save. */
final class SettingsEditor {
  interface Save {
    void accept(JSONObject config, List<String> changed) throws Exception;
  }

  private final MainActivity host;
  final FormModel model;
  private final JSONObject metadata, labels;
  private final String title;
  private final Runnable closed;
  private final Save save;
  private final Set<String> readOnly;
  private List<String> path = new ArrayList<>();
  private Button saveButton;
  private static final Map<String, String> NAMES =
      Map.ofEntries(
          Map.entry("enable", "启用"),
          Map.entry("enabled", "启用"),
          Map.entry("active", "启用服务"),
          Map.entry("name", "名称"),
          Map.entry("system_prompt", "系统提示词"),
          Map.entry("begin_dialogs", "开场对话"),
          Map.entry("persona_id", "人格标识"),
          Map.entry("provider_settings", "模型与对话"),
          Map.entry("platform", "机器人平台"),
          Map.entry("dashboard", "管理面板"),
          Map.entry("admins_id", "管理员"),
          Map.entry("timezone", "时区"),
          Map.entry("log_level", "日志级别"),
          Map.entry("api_base", "服务地址"),
          Map.entry("api_key", "API 密钥"),
          Map.entry("key", "访问密钥"),
          Map.entry("model", "模型名称"),
          Map.entry("max_tokens", "最大输出长度"),
          Map.entry("temperature", "温度"),
          Map.entry("url", "服务地址"),
          Map.entry("command", "启动命令"),
          Map.entry("args", "启动参数"),
          Map.entry("env", "环境变量"),
          Map.entry("headers", "请求头"),
          Map.entry("transport", "连接方式"),
          Map.entry("timeout", "超时时间"),
          Map.entry("tools", "工具列表"),
          Map.entry("skills", "技能列表"),
          Map.entry("provider", "模型提供商"),
          Map.entry("id", "标识"),
          Map.entry("host", "监听地址"),
          Map.entry("port", "端口"));

  SettingsEditor(
      MainActivity host,
      String title,
      JSONObject config,
      JSONObject metadata,
      JSONObject labels,
      Set<String> readOnly,
      Runnable closed,
      Save save)
      throws Exception {
    this.host = host;
    this.title = title;
    this.model = new FormModel(config);
    this.metadata = metadata == null ? new JSONObject() : metadata;
    this.labels = labels == null ? new JSONObject() : labels;
    this.readOnly = readOnly;
    this.closed = closed;
    this.save = save;
  }

  private Object translated(Object value) {
    if (value instanceof String) {
      String key = (String) value;
      Object found = labels.opt(key);
      if (found != null) return found;
      if (key.endsWith(".description") || key.endsWith(".hint") || key.endsWith(".labels"))
        return "";
    }
    return value;
  }

  private String title(List<String> at, JSONObject meta) {
    String k = at.get(at.size() - 1);
    Object description = translated(meta.opt("description"));
    if (description instanceof String && !((String) description).isBlank())
      return (String) description;
    if (k.matches("[0-9]+")) return "第 " + (Integer.parseInt(k) + 1) + " 项";
    return NAMES.getOrDefault(k, k.replace('_', ' '));
  }

  private String hint(JSONObject meta) {
    Object h = translated(meta.opt("hint"));
    return h instanceof String ? (String) h : "";
  }

  private List<String> child(String key) {
    List<String> p = new ArrayList<>(path);
    p.add(key);
    return p;
  }

  private void changed() {
    try {
      int count = model.changes().size();
      if (saveButton != null) {
        saveButton.setEnabled(count > 0);
        saveButton.setAlpha(count > 0 ? 1f : 0.45f);
        saveButton.setText(count == 0 ? "没有待保存的更改" : "保存更改（" + count + "）");
      }
    } catch (Exception e) {
      host.error(e);
    }
  }

  private boolean secret(String key, JSONObject meta) {
    return meta.optBoolean("obvious_hint")
        || key.toLowerCase(Locale.ROOT)
            .matches(".*(password|passwd|secret|token|api.?key|authorization).*");
  }

  private String summary(Object value, String key, JSONObject meta) {
    if (value instanceof JSONObject) return ((JSONObject) value).length() + " 项设置";
    if (value instanceof JSONArray) return ((JSONArray) value).length() + " 项";
    if (value == JSONObject.NULL) return "未设置";
    if (secret(key, meta) && !value.toString().isEmpty()) return "••••••••";
    String text = value.toString().replace('\n', ' ');
    return text.isEmpty() ? "未填写" : text.length() > 64 ? text.substring(0, 64) + "…" : text;
  }

  void show() {
    String heading = path.isEmpty() ? title : title(path, FormModel.metadata(metadata, path));
    host.screen(heading, path.isEmpty() ? "按字段调整配置，确认保存后生效" : "更改暂存于当前草稿", this::back);
    LinearLayout searchBox = host.card("", "");
    EditText search = host.input(searchBox, "搜索设置", "", false, false);
    LinearLayout rows = host.vertical();
    host.content.addView(rows);
    Runnable render =
        () -> {
          try {
            renderRows(rows, search.getText().toString());
          } catch (Exception e) {
            host.error(e);
          }
        };
    search.addTextChangedListener(host.watcher(render));
    render.run();
    try {
      if (model.get(path) instanceof JSONArray)
        host.button(host.content, "＋ 添加一项", this::addArrayItem);
      if (!path.isEmpty()
          && model.get(path) instanceof JSONObject
          && Set.of("env", "headers").contains(path.get(path.size() - 1)))
        host.button(host.content, "＋ 添加条目", this::addMapEntry);
    } catch (Exception e) {
      host.error(e);
    }
    LinearLayout footer = host.vertical();
    footer.setPadding(host.dp(18), host.dp(8), host.dp(18), host.dp(12));
    footer.setBackground(host.background(0xeef9fbff, 24));
    saveButton =
        host.primaryButton(
            footer,
            "保存更改",
            () -> {
              try {
                List<String> changed = model.changes();
                if (changed.isEmpty()) {
                  host.toast("没有更改");
                  return;
                }
                List<String> readable = new ArrayList<>();
                for (String key : changed)
                  readable.add(
                      title(
                          Arrays.asList(key.split(java.util.regex.Pattern.quote("."))),
                          FormModel.metadata(
                              metadata,
                              Arrays.asList(key.split(java.util.regex.Pattern.quote("."))))));
                JSONObject snapshot = new JSONObject(model.draft.toString());
                host.confirm(
                    "保存配置",
                    String.format(
                        Locale.CHINA,
                        "将保存 %d 项更改：\n%s",
                        changed.size(),
                        String.join("、", readable)),
                    () -> {
                      try {
                        save.accept(snapshot, changed);
                      } catch (Exception e) {
                        host.error(e);
                      }
                    });
              } catch (Exception e) {
                host.error(e);
              }
            });
    host.page.addView(footer);
    changed();
  }

  private void renderRows(LinearLayout rows, String query) throws Exception {
    rows.removeAllViews();
    Object node = model.get(path);
    List<String> keys = new ArrayList<>();
    if (node instanceof JSONObject) ((JSONObject) node).keys().forEachRemaining(keys::add);
    else if (node instanceof JSONArray)
      for (int i = 0; i < ((JSONArray) node).length(); i++) keys.add(Integer.toString(i));
    String needle = query.toLowerCase(Locale.ROOT);
    int count = 0;
    for (String key : keys) {
      List<String> at = child(key);
      Object value = model.get(at);
      JSONObject meta = FormModel.metadata(metadata, at);
      String name = title(at, meta), hint = hint(meta);
      if (!(name + key + hint).toLowerCase(Locale.ROOT).contains(needle)) continue;
      count++;
      LinearLayout group = host.group(rows);
      boolean locked = path.isEmpty() && readOnly.contains(key);
      if (value instanceof Boolean) {
        Switch toggle = new Switch(host);
        toggle.setText(name);
        toggle.setTextSize(16);
        toggle.setTextColor(MainActivity.INK);
        toggle.setChecked((Boolean) value);
        toggle.setEnabled(!locked);
        toggle.setPadding(host.dp(16), host.dp(18), host.dp(16), host.dp(18));
        group.addView(toggle);
        toggle.setOnCheckedChangeListener(
            (b, checked) -> {
              try {
                model.set(at, checked);
                changed();
              } catch (Exception e) {
                host.error(e);
              }
            });
        if (!hint.isEmpty()) {
          TextView h = host.label(hint, 12, MainActivity.MUTED);
          h.setPadding(host.dp(16), 0, host.dp(16), host.dp(12));
          group.addView(h);
        }
      } else {
        host.navRow(
            group,
            name,
            summary(value, key, meta) + (locked ? " · 标识不可修改" : ""),
            value instanceof JSONObject ? "◈" : value instanceof JSONArray ? "≡" : "",
            locked ? null : () -> edit(at, meta));
        if (!hint.isEmpty()) {
          TextView h = host.label(hint, 12, MainActivity.MUTED);
          h.setPadding(host.dp(16), 0, host.dp(16), host.dp(12));
          group.addView(h);
        }
      }
      if (node instanceof JSONArray) {
        int index = Integer.parseInt(key);
        host.textAction(
            group,
            "删除此项",
            () ->
                host.confirm(
                    "删除列表项",
                    "删除仅修改当前草稿，保存后生效。",
                    () -> {
                      ((JSONArray) node).remove(index);
                      show();
                    }));
      }
    }
    if (count == 0)
      rows.addView(host.label(query.isEmpty() ? "此处没有可编辑设置" : "没有匹配的设置", 14, MainActivity.MUTED));
  }

  private void edit(List<String> at, JSONObject meta) {
    try {
      Object before = model.get(at);
      String title = title(at, meta);
      if (before instanceof JSONObject || before instanceof JSONArray) {
        path = at;
        show();
        return;
      }
      JSONArray options = meta.optJSONArray("options");
      if (options == null) options = meta.optJSONArray("enum");
      if (options != null && options.length() > 0) {
        JSONArray choices = options;
        Object labelsValue = translated(meta.opt("labels"));
        JSONArray friendly = labelsValue instanceof JSONArray ? (JSONArray) labelsValue : null;
        String[] names = new String[choices.length()];
        int selected = -1;
        for (int i = 0; i < names.length; i++) {
          names[i] =
              friendly == null
                  ? choices.get(i).toString()
                  : friendly.optString(i, choices.get(i).toString());
          if (choices.get(i).toString().equals(before.toString())) selected = i;
        }
        new AlertDialog.Builder(host)
            .setTitle(title)
            .setSingleChoiceItems(
                names,
                selected,
                (d, index) -> {
                  try {
                    model.set(at, choices.get(index));
                    d.dismiss();
                    show();
                  } catch (Exception e) {
                    host.error(e);
                  }
                })
            .setNegativeButton("取消", null)
            .show();
        return;
      }
      if (!(before instanceof Number)
          && !secret(at.get(at.size() - 1), meta)
          && (before.toString().length() > 100
              || before.toString().contains("\n")
              || at.get(at.size() - 1).contains("prompt"))) {
        host.editLongText(
            title,
            before == JSONObject.NULL ? "" : before.toString(),
            false,
            value -> {
              try {
                model.set(at, value);
                show();
              } catch (Exception e) {
                host.error(e);
              }
            });
        return;
      }
      LinearLayout panel = host.vertical();
      panel.setPadding(host.dp(20), 0, host.dp(20), 0);
      EditText input =
          host.input(
              panel,
              title,
              before == JSONObject.NULL ? "" : before.toString(),
              secret(at.get(at.size() - 1), meta),
              false);
      if (before instanceof Number)
        input.setInputType(
            InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);
      TextView warning = host.label("", 12, 0xffa26537);
      panel.addView(warning);
      input.addTextChangedListener(
          host.watcher(
              () ->
                  warning.setText(
                      String.join(
                          "\n",
                          Helpers.warnings(at.get(at.size() - 1), input.getText().toString())))));
      AlertDialog dialog =
          new AlertDialog.Builder(host)
              .setTitle(title)
              .setView(panel)
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
                          model.set(at, FormModel.parse(input.getText().toString(), before, meta));
                          dialog.dismiss();
                          show();
                        } catch (Exception e) {
                          input.setError(e.getMessage());
                        }
                      }));
      dialog.show();
    } catch (Exception e) {
      host.error(e);
    }
  }

  private void addMapEntry() {
    LinearLayout panel = host.vertical();
    panel.setPadding(host.dp(20), 0, host.dp(20), 0);
    EditText key = host.input(panel, "名称", "", false, false),
        value = host.input(panel, "值", "", false, false);
    AlertDialog dialog =
        new AlertDialog.Builder(host)
            .setTitle("添加条目")
            .setView(panel)
            .setNegativeButton("取消", null)
            .setPositiveButton("添加", null)
            .create();
    dialog.setOnShowListener(
        d ->
            dialog
                .getButton(-1)
                .setOnClickListener(
                    v -> {
                      try {
                        String name = key.getText().toString().trim();
                        JSONObject map = (JSONObject) model.get(path);
                        if (name.isEmpty() || map.has(name)) {
                          key.setError("名称不能为空或重复");
                          return;
                        }
                        map.put(name, value.getText().toString());
                        dialog.dismiss();
                        show();
                      } catch (Exception e) {
                        host.error(e);
                      }
                    }));
    dialog.show();
  }

  private void addArrayItem() {
    try {
      JSONArray array = (JSONArray) model.get(path);
      JSONObject meta = FormModel.metadata(metadata, path), item = meta.optJSONObject("items");
      Object value =
          array.length() > 0 && array.get(0) instanceof JSONObject
              ? new JSONObject(array.getJSONObject(0).toString())
              : item == null ? "" : FormModel.defaultValue(item);
      array.put(value);
      show();
    } catch (Exception e) {
      host.error(e);
    }
  }

  private void back() {
    if (!path.isEmpty()) {
      path = new ArrayList<>(path.subList(0, path.size() - 1));
      show();
      return;
    }
    try {
      if (model.changes().isEmpty()) closed.run();
      else host.confirm("放弃未保存的更改？", "当前草稿尚未写入服务器。", closed);
    } catch (Exception e) {
      host.error(e);
    }
  }
}
