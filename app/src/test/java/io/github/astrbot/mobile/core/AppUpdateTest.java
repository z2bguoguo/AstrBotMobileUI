package io.github.astrbot.mobile.core;

import static org.junit.Assert.*;

import java.io.*;
import java.security.*;
import java.util.*;
import org.json.*;
import org.junit.Test;

public class AppUpdateTest {
  private static final String BASE =
      "https://github.com/" + AppUpdate.REPO + "/releases/download/v1/";

  private JSONObject release(long code, boolean preview, boolean draft) throws Exception {
    return new JSONObject()
        .put("prerelease", preview)
        .put("draft", draft)
        .put("body", "notes")
        .put(
            "assets",
            new JSONArray()
                .put(
                    new JSONObject()
                        .put("name", "update-" + code + ".json")
                        .put("browser_download_url", BASE + "update-" + code + ".json")));
  }

  private JSONObject manifest() throws Exception {
    return new JSONObject()
        .put("versionCode", 12)
        .put("versionName", "0.1.2")
        .put("url", BASE + "app.apk")
        .put("sha256", "a".repeat(64))
        .put("size", 1024);
  }

  private AppUpdate.Candidate candidate() {
    return new AppUpdate.Candidate(12, BASE + "update-12.json", "", false);
  }

  @Test
  public void choosesHighestCodeRegardlessOfReleaseOrder() throws Exception {
    JSONArray releases =
        new JSONArray()
            .put(release(10, false, false))
            .put(release(12, false, false))
            .put(release(11, false, false));
    assertEquals(12, AppUpdate.select(releases, 9, false).code());
  }

  @Test
  public void stableExcludesDraftAndPreviews() throws Exception {
    JSONArray releases =
        new JSONArray()
            .put(release(12, true, false))
            .put(release(15, false, true))
            .put(release(10, false, false));
    assertEquals(10, AppUpdate.select(releases, 9, false).code());
    assertEquals(12, AppUpdate.select(releases, 9, true).code());
  }

  @Test
  public void neverDowngradesOrReoffersInstalledCode() throws Exception {
    assertNull(AppUpdate.select(new JSONArray().put(release(12, false, false)), 12, true));
    assertNull(AppUpdate.select(new JSONArray().put(release(12, false, false)), 13, true));
  }

  @Test
  public void ignoresOlderReleasesWithoutMetadata() throws Exception {
    assertNull(
        AppUpdate.select(
            new JSONArray().put(new JSONObject().put("assets", new JSONArray())), 1, true));
  }

  @Test
  public void refusesForeignAndInsecureUrls() throws Exception {
    for (String bad :
        new String[] {
          "http://github.com/" + AppUpdate.REPO + "/releases/download/v/a.apk",
          "https://github.com.evil.test/" + AppUpdate.REPO + "/releases/download/v/a.apk",
          "https://github.com/other/repo/releases/download/v/a.apk",
          "https://name@github.com/" + AppUpdate.REPO + "/releases/download/v/a.apk",
          BASE + "a.apk?token=x",
          "invalid"
        }) assertFalse(bad, AppUpdate.releaseUrl(bad));
    assertTrue(AppUpdate.releaseUrl(BASE + "app.apk"));
  }

  @Test
  public void ignoresForeignManifestEvenWithNewerCode() throws Exception {
    JSONObject r = release(99, false, false);
    r.getJSONArray("assets")
        .getJSONObject(0)
        .put("browser_download_url", "https://evil.test/update-99.json");
    assertNull(AppUpdate.select(new JSONArray().put(r), 1, true));
  }

  @Test
  public void validManifestPreservesFields() throws Exception {
    AppUpdate.Package p = AppUpdate.parse(manifest(), candidate());
    assertEquals(12, p.code());
    assertEquals(1024, p.size());
    assertEquals("0.1.2", p.version());
  }

  @Test
  public void rejectsMismatchedCodeHashSizeAndDownload() throws Exception {
    Object[][] cases = {
      {"versionCode", 13},
      {"size", 0},
      {"size", 128L * 1024 * 1024 + 1},
      {"sha256", "bad"},
      {"url", BASE + "file.zip"},
      {"url", "https://evil.test/app.apk"},
      {"versionName", ""}
    };
    for (Object[] c : cases) {
      try {
        AppUpdate.parse(manifest().put((String) c[0], c[1]), candidate());
        fail("Accepted " + c[0]);
      } catch (IllegalArgumentException expected) {
      }
    }
  }

  @Test
  public void verifiesBytesAndReportsProgress() throws Exception {
    byte[] data = "valid apk fixture".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    String hash =
        java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    AppUpdate.Package update = new AppUpdate.Package(12, "v", BASE + "app.apk", hash, data.length);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    List<Integer> progress = new ArrayList<>();
    AppUpdate.copyVerified(
        new ByteArrayInputStream(data), output, update, progress::add, () -> false);
    assertArrayEquals(data, output.toByteArray());
    assertEquals(Integer.valueOf(100), progress.get(progress.size() - 1));
  }

  @Test
  public void failsForTruncatedOversizedCorruptAndCancelledDownloads() throws Exception {
    byte[] data = {1, 2, 3};
    String hash =
        java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    for (int scenario = 0; scenario < 4; scenario++) {
      long size = scenario == 0 ? 4 : scenario == 1 ? 2 : 3;
      String expected = scenario == 2 ? "0".repeat(64) : hash;
      boolean cancelled = scenario == 3;
      try {
        AppUpdate.copyVerified(
            new ByteArrayInputStream(data),
            new ByteArrayOutputStream(),
            new AppUpdate.Package(12, "v", BASE + "app.apk", expected, size),
            p -> {},
            () -> cancelled);
        fail("Accepted scenario " + scenario);
      } catch (IOException expectedFailure) {
      }
    }
  }
}
