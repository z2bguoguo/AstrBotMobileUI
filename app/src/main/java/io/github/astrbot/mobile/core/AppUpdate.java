package io.github.astrbot.mobile.core;

import java.io.*;
import java.net.URI;
import java.security.*;
import java.util.Locale;
import java.util.function.*;
import org.json.*;

/** Release selection uses Android version codes, never lexicographic version names. */
public final class AppUpdate {
  public static final String REPO = "z2bguoguo/AstrBotMobileUI";

  public record Candidate(long code, String manifest, String notes, boolean preview) {}

  public record ScriptPackage(long code, String architecture, String url, String hash, long size) {}

  public static ScriptPackage parseScript(JSONObject manifest, Candidate candidate)
      throws JSONException {
    JSONObject c = manifest.optJSONObject("core");
    if (c == null) return null;
    long version = c.getLong("version"), size = c.getLong("size");
    String architecture = c.getString("architecture"),
        url = c.getString("url"),
        hash = c.getString("sha256");
    if (version != candidate.code()
        || !architecture.matches("[a-f0-9]{64}")
        || !releaseUrl(url)
        || !hash.matches("[a-f0-9]{64}")
        || size <= 0
        || size > 8 * 1024 * 1024)
      throw new IllegalArgumentException("Invalid script update metadata");
    return new ScriptPackage(version, architecture, url, hash, size);
  }

  public record Package(long code, String version, String url, String sha256, long size) {}

  public static Candidate select(JSONArray releases, long installed, boolean previews)
      throws JSONException {
    Candidate best = null;
    for (int i = 0; i < releases.length(); i++) {
      JSONObject r = releases.getJSONObject(i);
      if (r.optBoolean("draft") || (!previews && r.optBoolean("prerelease"))) continue;
      JSONArray assets = r.optJSONArray("assets");
      if (assets == null) continue;
      for (int j = 0; j < assets.length(); j++) {
        JSONObject a = assets.getJSONObject(j);
        String name = a.optString("name");
        if (!name.matches("update-[0-9]{1,10}\\.json")) continue;
        long code = Long.parseLong(name.substring(7, name.length() - 5));
        String url = a.optString("browser_download_url");
        if (code > installed && (best == null || code > best.code()) && releaseUrl(url))
          best = new Candidate(code, url, r.optString("body"), r.optBoolean("prerelease"));
      }
    }
    return best;
  }

  public static boolean releaseUrl(String value) {
    try {
      URI u = URI.create(value);
      return "https".equals(u.getScheme())
          && "github.com".equals(u.getHost())
          && u.getUserInfo() == null
          && u.getPort() == -1
          && u.getPath().startsWith("/" + REPO + "/releases/download/")
          && u.getQuery() == null
          && u.getFragment() == null;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  public static Package parse(JSONObject o, Candidate candidate) throws JSONException {
    long code = o.getLong("versionCode"), size = o.getLong("size");
    String version = o.getString("versionName"), url = o.getString("url");
    String hash = o.getString("sha256").toLowerCase(Locale.ROOT);
    if (code != candidate.code()
        || code > Integer.MAX_VALUE
        || !releaseUrl(url)
        || !url.endsWith(".apk")
        || !hash.matches("[0-9a-f]{64}")
        || size <= 0
        || size > 128L * 1024 * 1024
        || version.isBlank()) throw new IllegalArgumentException("更新元数据不完整或不匹配");
    return new Package(code, version, url, hash, size);
  }

  public static void copyVerified(
      InputStream in,
      OutputStream out,
      Package update,
      IntConsumer progress,
      BooleanSupplier cancelled)
      throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] buffer = new byte[32768];
      long total = 0;
      int previous = -1;
      for (int count; (count = in.read(buffer)) != -1; ) {
        if (cancelled.getAsBoolean()) throw new InterruptedIOException("下载已取消");
        total += count;
        if (total > update.size()) throw new IOException("安装包大小不匹配");
        digest.update(buffer, 0, count);
        out.write(buffer, 0, count);
        int percent = (int) (total * 100 / update.size());
        if (percent != previous) {
          previous = percent;
          progress.accept(percent);
        }
      }
      StringBuilder hash = new StringBuilder();
      for (byte b : digest.digest()) hash.append(String.format(Locale.ROOT, "%02x", b & 255));
      if (total != update.size() || !hash.toString().equals(update.sha256()))
        throw new IOException("安装包校验失败，请重新下载");
    } catch (NoSuchAlgorithmException e) {
      throw new IOException(e);
    }
  }

  private AppUpdate() {}
}
