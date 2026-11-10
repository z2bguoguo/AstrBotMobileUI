package io.github.astrbot.mobile.core;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.json.*;
import org.junit.Test;

public class SignedCoreTest {
  private KeyPair key() throws Exception {
    KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
    g.initialize(2048);
    return g.generateKeyPair();
  }

  private JSONObject payload() throws Exception {
    return new JSONObject()
        .put("abi", 1)
        .put("architecture", "test-architecture")
        .put("version", 12)
        .put("locales.json", "{}")
        .put("fields.json", "{}")
        .put("main.js", "function dispatch(){return '{\"abi\":1}';}");
  }

  private String envelope(JSONObject payload, KeyPair pair) throws Exception {
    byte[] data = payload.toString().getBytes(StandardCharsets.UTF_8);
    Signature s = Signature.getInstance("SHA256withRSA");
    s.initSign(pair.getPrivate());
    s.update(data);
    return new JSONObject()
        .put("payload", Base64.getEncoder().encodeToString(data))
        .put("signature", Base64.getEncoder().encodeToString(s.sign()))
        .toString();
  }

  @Test
  public void acceptsMatchingSignedCore() throws Exception {
    KeyPair pair = key();
    assertEquals(
        12,
        SignedCore.verify(
                envelope(payload(), pair), List.of(pair.getPublic()), "test-architecture", 11)
            .getInt("version"));
  }

  @Test
  public void refusesForeignSignaturesAndModifiedPayload() throws Exception {
    KeyPair pair = key();
    String valid = envelope(payload(), pair);
    assertThrows(
        SecurityException.class,
        () -> SignedCore.verify(valid, List.of(key().getPublic()), "test-architecture", 11));
    JSONObject changed = new JSONObject(valid);
    changed.put(
        "payload",
        Base64.getEncoder()
            .encodeToString(
                payload().put("version", 100).toString().getBytes(StandardCharsets.UTF_8)));
    assertThrows(
        SecurityException.class,
        () ->
            SignedCore.verify(
                changed.toString(), List.of(pair.getPublic()), "test-architecture", 11));
  }

  @Test
  public void rejectsArchitectureMismatchDowngradeAndMalformedScript() throws Exception {
    KeyPair pair = key();
    assertThrows(
        Exception.class,
        () ->
            SignedCore.verify(
                envelope(payload(), pair), List.of(pair.getPublic()), "other-architecture", 11));
    assertThrows(
        Exception.class,
        () ->
            SignedCore.verify(
                envelope(payload(), pair), List.of(pair.getPublic()), "test-architecture", 12));
    assertThrows(
        Exception.class,
        () ->
            SignedCore.verify(
                envelope(payload().put("main.js", "{{invalid"), pair),
                List.of(pair.getPublic()),
                "test-architecture",
                11));
  }
}
