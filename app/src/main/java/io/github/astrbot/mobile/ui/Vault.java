package io.github.astrbot.mobile.ui;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import org.json.*;

/** No plaintext credentials, export, or backup. A corrupt vault is never silently reset. */
final class Vault {
  private final Context context;

  Vault(Context context) {
    this.context = context;
  }

  private javax.crypto.SecretKey key() throws Exception {
    KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
    ks.load(null);
    String alias = "astrbot.mobile.vault.v1";
    if (!ks.containsAlias(alias)) {
      KeyGenerator gen =
          KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
      gen.init(
          new KeyGenParameterSpec.Builder(
                  alias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
              .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
              .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
              .build());
      gen.generateKey();
    }
    return (javax.crypto.SecretKey) ks.getKey(alias, null);
  }

  JSONObject load() throws Exception {
    String value = context.getSharedPreferences("vault", 0).getString("data", "");
    if (value.isEmpty())
      return new JSONObject()
          .put("connections", new JSONArray())
          .put("presets", new JSONArray())
          .put("extensions", new JSONArray());
    String[] parts = value.split(":");
    if (parts.length != 2) throw new IllegalStateException("配置存储损坏");
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(
        Cipher.DECRYPT_MODE,
        key(),
        new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
    return new JSONObject(
        new String(
            cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8));
  }

  void save(JSONObject data) throws Exception {
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.ENCRYPT_MODE, key());
    String value =
        Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)
            + ":"
            + Base64.encodeToString(
                cipher.doFinal(data.toString().getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
    if (!context.getSharedPreferences("vault", 0).edit().putString("data", value).commit())
      throw new IllegalStateException("配置保存失败");
  }
}
