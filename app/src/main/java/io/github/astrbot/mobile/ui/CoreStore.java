package io.github.astrbot.mobile.ui;

import android.content.Context;
import android.content.pm.*;
import android.os.Build;
import android.util.AtomicFile;
import io.github.astrbot.mobile.BuildConfig;
import io.github.astrbot.mobile.core.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.util.*;
import org.json.*;

/** Signed JSON bundles, atomic activation and one-generation recovery. No downloaded DEX. */
@android.annotation.SuppressLint(
    "ApplySharedPref") // Crash-recovery markers must reach disk synchronously.
public final class CoreStore {
  @android.annotation.SuppressLint("StaticFieldLeak") // Application context only.
  private static Context context;

  private static JSONObject active;
  private static final int LIMIT = 8 * 1024 * 1024;

  static void initialize(Context ctx) {
    context = ctx.getApplicationContext();
    android.content.SharedPreferences boot = context.getSharedPreferences("core-boot", 0);
    if (boot.getBoolean("attempted", false)) {
      try {
        rollback();
      } catch (Exception e) {
        new AtomicFile(new File(context.getFilesDir(), "core-active.json")).delete();
      }
      boot.edit().clear().commit();
    }
    if (boot.getBoolean("pending", false)) boot.edit().putBoolean("attempted", true).commit();
    try {
      File file = new File(context.getFilesDir(), "core-active.json");
      if (file.exists()) {
        try {
          JSONObject candidate = verify(read(new AtomicFile(file).openRead()));
          activate(candidate);
          return;
        } catch (Exception e) {
          new AtomicFile(file).delete();
        }
      }
      activate(bundled());
    } catch (Exception e) {
      throw new IllegalStateException("Cannot load bundled script core", e);
    }
  }

  private static JSONObject bundled() throws Exception {
    JSONObject payload =
        new JSONObject()
            .put("architecture", BuildConfig.ARCHITECTURE)
            .put("version", BuildConfig.CORE_VERSION)
            .put("abi", 1);
    for (String file : context.getAssets().list("core"))
      if (file.endsWith(".js") || file.endsWith(".json"))
        payload.put(file, read(context.getAssets().open("core/" + file)));
    return payload;
  }

  private static String read(InputStream in) throws Exception {
    return new String(HttpTransport.readLimited(in, LIMIT), StandardCharsets.UTF_8);
  }

  static void healthy() {
    context.getSharedPreferences("core-boot", 0).edit().clear().apply();
  }

  static long version() {
    return active.optLong("version", BuildConfig.CORE_VERSION);
  }

  static String data(String key) {
    return active.optString(key, "{}");
  }

  static boolean compatible(String architecture) {
    return BuildConfig.ARCHITECTURE.equals(architecture);
  }

  private static void activate(JSONObject payload) throws Exception {
    CoreRuntime.configure(CoreSource.script(payload));
    active = payload;
  }

  @SuppressWarnings("deprecation")
  static JSONObject verify(String envelope) throws Exception {
    int flags =
        Build.VERSION.SDK_INT >= 28
            ? PackageManager.GET_SIGNING_CERTIFICATES
            : PackageManager.GET_SIGNATURES;
    PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), flags);
    android.content.pm.Signature[] certs =
        Build.VERSION.SDK_INT >= 28 ? info.signingInfo.getApkContentsSigners() : info.signatures;
    java.util.List<java.security.PublicKey> keys = new java.util.ArrayList<>();
    for (android.content.pm.Signature cert : certs)
      keys.add(
          CertificateFactory.getInstance("X.509")
              .generateCertificate(new ByteArrayInputStream(cert.toByteArray()))
              .getPublicKey());
    return SignedCore.verify(envelope, keys, BuildConfig.ARCHITECTURE, BuildConfig.CORE_VERSION);
  }

  static void stage(String envelope, long expected) throws Exception {
    JSONObject payload = verify(envelope);
    if (payload.getLong("version") != expected || expected <= version())
      throw new IOException("Core version does not increase");
    File current = new File(context.getFilesDir(), "core-active.json");
    if (current.exists())
      write(
          new File(context.getFilesDir(), "core-previous.json"),
          read(new AtomicFile(current).openRead()));
    write(current, envelope);
    context
        .getSharedPreferences("core-boot", 0)
        .edit()
        .putBoolean("pending", true)
        .putBoolean("attempted", false)
        .commit();
  }

  static void rollback() throws Exception {
    File previous = new File(context.getFilesDir(), "core-previous.json");
    File current = new File(context.getFilesDir(), "core-active.json");
    if (previous.exists()) {
      String envelope = read(new AtomicFile(previous).openRead());
      verify(envelope);
      write(current, envelope);
      new AtomicFile(previous).delete();
    } else new AtomicFile(current).delete();
  }

  private static void write(File file, String value) throws Exception {
    AtomicFile atomic = new AtomicFile(file);
    FileOutputStream out = null;
    try {
      out = atomic.startWrite();
      out.write(value.getBytes(StandardCharsets.UTF_8));
      atomic.finishWrite(out);
    } catch (Exception e) {
      if (out != null) atomic.failWrite(out);
      throw e;
    }
  }

  private CoreStore() {}
}
