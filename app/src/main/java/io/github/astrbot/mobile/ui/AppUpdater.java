package io.github.astrbot.mobile.ui;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.*;
import io.github.astrbot.mobile.core.AppUpdate;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Foreground update flow; installation always remains under Android user control. */
final class AppUpdater {
  private final MainActivity activity;
  private final SharedPreferences prefs;
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private boolean busy, awaitingPermission;
  private volatile boolean closed;
  private AlertDialog progress;
  private AppUpdate.Package pending;

  AppUpdater(MainActivity activity) {
    this.activity = activity;
    prefs = activity.getSharedPreferences("updates", 0);
  }

  void startup() {
    if (prefs.getBoolean("check", true)
        && System.currentTimeMillis() - prefs.getLong("checked", 0) > 24L * 60 * 60 * 1000)
      check(false);
  }

  void settings(LinearLayout parent) {
    try {
      PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
      parent.addView(activity.label("当前版本 " + info.versionName, 14, MainActivity.MUTED));
    } catch (PackageManager.NameNotFoundException ignored) {
    }
    toggle(parent, "启动时自动检查（每天一次）", "check", true);
    toggle(parent, "发现更新后自动下载", "download", false);
    parent.addView(activity.label("自动下载仅在应用打开时进行，安装前仍需确认。", 13, MainActivity.MUTED));
    boolean preview = previews();
    activity.navRow(
        parent,
        "更新渠道",
        preview ? "正式版与预发布" : "仅正式版",
        "↻",
        () ->
            new AlertDialog.Builder(activity)
                .setTitle("更新渠道")
                .setSingleChoiceItems(
                    new String[] {"仅正式版", "正式版与预发布"},
                    preview ? 1 : 0,
                    (d, which) -> {
                      prefs.edit().putBoolean("preview", which == 1).remove("checked").apply();
                      d.dismiss();
                      activity.refreshUpdateSettings();
                    })
                .setNegativeButton("取消", null)
                .show());
    activity.button(parent, "立即检查更新", () -> check(true));
  }

  private boolean previews() {
    try {
      String version =
          activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName;
      return prefs.getBoolean(
          "preview", version != null && (version.contains("pre") || version.contains("dev")));
    } catch (PackageManager.NameNotFoundException e) {
      return prefs.getBoolean("preview", false);
    }
  }

  private void toggle(LinearLayout parent, String title, String key, boolean fallback) {
    Switch view = new Switch(activity);
    view.setText(title);
    view.setTextColor(MainActivity.INK);
    view.setChecked(prefs.getBoolean(key, fallback));
    view.setPadding(0, activity.dp(12), 0, activity.dp(12));
    parent.addView(view);
    view.setOnCheckedChangeListener((v, checked) -> prefs.edit().putBoolean(key, checked).apply());
  }

  private void ui(Runnable task) {
    activity.runOnUiThread(
        () -> {
          if (!closed && !activity.isFinishing() && !activity.isDestroyed()) task.run();
        });
  }

  void check(boolean manual) {
    if (busy) {
      if (manual) toast("正在检查或下载更新");
      return;
    }
    busy = true;
    if (manual) toast("正在检查更新…");
    boolean includePreview = previews();
    worker.execute(
        () -> {
          try {
            long installed =
                code(activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0));
            JSONArray releases =
                new JSONArray(
                    new String(
                        read(
                            "https://api.github.com/repos/"
                                + AppUpdate.REPO
                                + "/releases?per_page=100",
                            4 * 1024 * 1024),
                        StandardCharsets.UTF_8));
            AppUpdate.Candidate candidate = AppUpdate.select(releases, installed, includePreview);
            AppUpdate.Package update =
                candidate == null
                    ? null
                    : AppUpdate.parse(
                        new JSONObject(
                            new String(
                                read(candidate.manifest(), 64 * 1024), StandardCharsets.UTF_8)),
                        candidate);
            ui(
                () -> {
                  busy = false;
                  prefs.edit().putLong("checked", System.currentTimeMillis()).apply();
                  if (update == null) {
                    if (manual) toast("当前渠道已是最新版本");
                    return;
                  }
                  if (prefs.getBoolean("download", false)) download(update);
                  else
                    new AlertDialog.Builder(activity)
                        .setTitle("发现新版本 " + update.version())
                        .setMessage(
                            (candidate.preview() ? "预发布版本\n\n" : "正式版本\n\n")
                                + truncate(candidate.notes()))
                        .setPositiveButton("下载更新", (d, w) -> download(update))
                        .setNegativeButton("稍后", null)
                        .show();
                });
          } catch (Exception e) {
            ui(
                () -> {
                  busy = false;
                  if (manual) failure(e);
                });
          }
        });
  }

  private static String truncate(String s) {
    return s.length() > 6000 ? s.substring(0, 6000) + "…" : s;
  }

  private void download(AppUpdate.Package update) {
    if (busy) return;
    busy = true;
    pending = null;
    progress =
        new AlertDialog.Builder(activity)
            .setTitle("下载更新 " + update.version())
            .setMessage("正在连接…")
            .setCancelable(false)
            .setNegativeButton("后台下载", (d, w) -> {})
            .show();
    worker.execute(
        () -> {
          File part = new File(activity.getCacheDir(), "update.part");
          try {
            File target = UpdateProvider.apk(activity);
            if (target.exists() && !target.delete()) throw new IOException("无法清理旧安装包");
            HttpURLConnection connection = connect(update.url());
            try (InputStream in = connection.getInputStream();
                OutputStream out = new FileOutputStream(part)) {
              AppUpdate.copyVerified(
                  in,
                  out,
                  update,
                  percent -> ui(() -> progress.setMessage("已下载 " + percent + "%")),
                  () -> closed);
            } finally {
              connection.disconnect();
            }
            validate(part, update);
            if (!part.renameTo(target)) throw new IOException("无法保存安装包");
            ui(
                () -> {
                  busy = false;
                  progress.dismiss();
                  pending = update;
                  new AlertDialog.Builder(activity)
                      .setTitle("更新已就绪")
                      .setMessage("已验证安装包与当前应用使用相同签名，安装后保留本地配置。")
                      .setPositiveButton("安装更新", (d, w) -> install())
                      .setNegativeButton("稍后", null)
                      .show();
                });
          } catch (Exception e) {
            part.delete();
            ui(
                () -> {
                  busy = false;
                  progress.dismiss();
                  failure(e);
                });
          }
        });
  }

  @SuppressWarnings("deprecation")
  private void validate(File file, AppUpdate.Package update) throws Exception {
    PackageManager pm = activity.getPackageManager();
    int flags =
        Build.VERSION.SDK_INT >= 28
            ? PackageManager.GET_SIGNING_CERTIFICATES
            : PackageManager.GET_SIGNATURES;
    PackageInfo incoming = pm.getPackageArchiveInfo(file.getPath(), flags);
    PackageInfo current = pm.getPackageInfo(activity.getPackageName(), flags);
    if (incoming == null
        || !activity.getPackageName().equals(incoming.packageName)
        || code(incoming) != update.code()
        || code(incoming) <= code(current)) throw new IOException("安装包包名或版本不匹配");
    if (!signers(incoming).equals(signers(current)))
      throw new IOException("签名不同，无法覆盖安装。测试 APK 与正式签名版本可能使用不同密钥；请使用相同签名的版本更新。");
  }

  @SuppressWarnings("deprecation")
  private static Set<String> signers(PackageInfo info) throws Exception {
    Signature[] signatures =
        Build.VERSION.SDK_INT >= 28 ? info.signingInfo.getApkContentsSigners() : info.signatures;
    if (signatures == null || signatures.length == 0) throw new IOException("安装包缺少签名");
    Set<String> result = new HashSet<>();
    for (Signature signature : signatures)
      result.add(hex(MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())));
    return result;
  }

  @SuppressWarnings("deprecation")
  private static long code(PackageInfo info) {
    return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
  }

  private void install() {
    if (pending == null) return;
    try {
      if (!activity.getPackageManager().canRequestPackageInstalls()) {
        awaitingPermission = true;
        new AlertDialog.Builder(activity)
            .setTitle("允许安装应用更新")
            .setMessage("请在接下来的系统页面允许此应用安装更新，返回后继续安装。")
            .setPositiveButton(
                "前往设置",
                (d, w) -> {
                  try {
                    activity.startActivity(
                        new Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + activity.getPackageName())));
                  } catch (Exception e) {
                    awaitingPermission = false;
                    failure(e);
                  }
                })
            .setNegativeButton("取消", (d, w) -> awaitingPermission = false)
            .setOnCancelListener(d -> awaitingPermission = false)
            .show();
        return;
      }
      validate(UpdateProvider.apk(activity), pending);
      activity.startActivity(
          new Intent(Intent.ACTION_VIEW)
              .setDataAndType(
                  Uri.parse("content://" + activity.getPackageName() + ".updates/update.apk"),
                  "application/vnd.android.package-archive")
              .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
    } catch (Exception e) {
      failure(e);
    }
  }

  void resume() {
    if (awaitingPermission) {
      awaitingPermission = false;
      if (activity.getPackageManager().canRequestPackageInstalls()) install();
      else toast("未授权安装，可稍后重新检查更新");
    }
  }

  void close() {
    closed = true;
    worker.shutdownNow();
    if (progress != null) progress.dismiss();
  }

  private void toast(String message) {
    Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
  }

  private void failure(Exception e) {
    new AlertDialog.Builder(activity)
        .setTitle("更新未完成")
        .setMessage(e.getMessage())
        .setPositiveButton("知道了", null)
        .show();
  }

  private static String hex(byte[] bytes) {
    StringBuilder s = new StringBuilder();
    for (byte b : bytes) s.append(String.format(Locale.ROOT, "%02x", b & 255));
    return s.toString();
  }

  private static byte[] read(String url, int limit) throws IOException {
    HttpURLConnection connection = connect(url);
    try (InputStream in = connection.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[8192];
      for (int n; (n = in.read(buffer)) != -1; ) {
        if (out.size() + n > limit) throw new IOException("更新响应过大");
        out.write(buffer, 0, n);
      }
      return out.toByteArray();
    } finally {
      connection.disconnect();
    }
  }

  private static HttpURLConnection connect(String address) throws IOException {
    URL url = new URL(address);
    for (int i = 0; i < 6; i++) {
      if (!"https".equals(url.getProtocol()) || url.getUserInfo() != null)
        throw new IOException("更新需要 HTTPS");
      HttpURLConnection c = (HttpURLConnection) url.openConnection();
      c.setInstanceFollowRedirects(false);
      c.setConnectTimeout(15000);
      c.setReadTimeout(30000);
      c.setRequestProperty("User-Agent", "AstrBotMobileUI-Updater");
      int status = c.getResponseCode();
      if (status >= 300 && status < 400) {
        String location = c.getHeaderField("Location");
        c.disconnect();
        if (location == null) throw new IOException("无效下载重定向");
        url = new URL(url, location);
        continue;
      }
      if (status != 200) {
        c.disconnect();
        throw new IOException(
            status == 404 ? "更新不可访问：请确认仓库和 Release 已公开" : "更新服务器返回 HTTP " + status);
      }
      return c;
    }
    throw new IOException("更新重定向次数过多");
  }
}
