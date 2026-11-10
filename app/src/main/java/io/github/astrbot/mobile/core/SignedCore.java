package io.github.astrbot.mobile.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.json.*;

/** Trust verification belongs to the APK, never to the downloaded script. */
public final class SignedCore {
  public static JSONObject verify(
      String envelope, List<PublicKey> keys, String architecture, long minimum) throws Exception {
    if (envelope.length() > 8 * 1024 * 1024) throw new IOException("Core bundle too large");
    JSONObject obj = new JSONObject(envelope);
    byte[] bytes = Base64.getDecoder().decode(obj.getString("payload"));
    byte[] signature = Base64.getDecoder().decode(obj.getString("signature"));
    boolean trusted = false;
    for (PublicKey key : keys) {
      Signature verifier = Signature.getInstance("SHA256withRSA");
      verifier.initVerify(key);
      verifier.update(bytes);
      if (verifier.verify(signature)) trusted = true;
    }
    if (!trusted)
      throw new SecurityException("Core signing certificate does not match the installed APK");
    JSONObject payload = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
    if (!architecture.equals(payload.getString("architecture")) || payload.getInt("abi") != 1)
      throw new IOException("Architecture update requires APK installation");
    if (payload.getLong("version") <= minimum)
      throw new IOException("Core bundle is older than the APK");
    new JSONObject(payload.getString("locales.json"));
    new JSONObject(payload.getString("fields.json"));
    CoreRuntime.validate(CoreSource.script(payload));
    return payload;
  }

  private SignedCore() {}
}
