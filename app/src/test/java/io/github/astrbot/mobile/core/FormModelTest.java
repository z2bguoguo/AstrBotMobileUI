package io.github.astrbot.mobile.core;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.*;
import org.json.*;
import org.junit.Test;

public class FormModelTest {
  @Test
  public void editingDraftDoesNotMutateLoadedOrUnknownFields() throws Exception {
    JSONObject input = new JSONObject("{\"enabled\":true,\"future_option\":{\"mode\":\"keep\"}}");
    FormModel form = new FormModel(input);
    form.set(List.of("enabled"), false);
    assertTrue(input.getBoolean("enabled"));
    assertTrue(form.original.getBoolean("enabled"));
    assertEquals("keep", form.draft.getJSONObject("future_option").getString("mode"));
    assertEquals(List.of("enabled"), form.changes());
  }

  @Test
  public void nestedChangeAndRevertTracked() throws Exception {
    FormModel form = new FormModel(new JSONObject("{\"chat\":{\"prompt\":\"original\"}}"));
    form.set(List.of("chat", "prompt"), "new\ntext");
    assertEquals(List.of("chat.prompt"), form.changes());
    form.set(List.of("chat", "prompt"), "original");
    assertTrue(form.changes().isEmpty());
  }

  @Test
  public void arrayEntriesRetainTheirTypes() throws Exception {
    FormModel f = new FormModel(new JSONObject("{\"admins\":[\"group:123\",\"old\"]}"));
    f.set(List.of("admins", "1"), "group:456");
    assertEquals("group:123", f.draft.getJSONArray("admins").getString(0));
    assertEquals(List.of("admins"), f.changes());
  }

  @Test
  public void numericFieldsRejectFractionForInteger() {
    assertThrows(IllegalArgumentException.class, () -> FormModel.parse("2.5", 3, new JSONObject()));
  }

  @Test
  public void integerAndDecimalInputRemainNumbers() {
    assertEquals(new BigDecimal("123"), FormModel.parse("123", 1, new JSONObject()));
    assertEquals(new BigDecimal("0.7"), FormModel.parse("0.7", 0.2, new JSONObject()));
  }

  @Test
  public void numericBoundsValidated() throws Exception {
    JSONObject meta = new JSONObject().put("minimum", 1).put("maximum", 10);
    assertThrows(IllegalArgumentException.class, () -> FormModel.parse("0", 5, meta));
    assertThrows(IllegalArgumentException.class, () -> FormModel.parse("11", 5, meta));
  }

  @Test
  public void whitespaceAndGroupIdsPreserved() {
    assertEquals("  prompt\n", FormModel.parse("  prompt\n", "", new JSONObject()));
    assertEquals("123", FormModel.parse("123", "group:1", new JSONObject()));
  }

  @Test
  public void booleanInputDoesNotBecomeTruthyString() {
    assertEquals(false, FormModel.parse("false", true, new JSONObject()));
    assertThrows(
        IllegalArgumentException.class, () -> FormModel.parse("yes", true, new JSONObject()));
  }

  @Test
  public void dottedMetadataAndNestedMetadataResolve() throws Exception {
    JSONObject meta =
        new JSONObject(
            "{\"group\":{\"metadata\":{\"system\":{\"items\":{\"dashboard.port\":{\"type\":\"int\",\"description\":\"端口\"}}}}}}");
    assertEquals(
        "端口", FormModel.metadata(meta, List.of("dashboard", "port")).getString("description"));
  }

  @Test
  public void defaultsAreDetachedCopies() throws Exception {
    JSONObject meta = new JSONObject("{\"default\":{\"enabled\":true}}");
    JSONObject copy = (JSONObject) FormModel.defaultValue(meta);
    copy.put("enabled", false);
    assertTrue(meta.getJSONObject("default").getBoolean("enabled"));
  }

  @Test
  public void listRemovalDoesNotAffectOriginal() throws Exception {
    FormModel f = new FormModel(new JSONObject("{\"args\":[\"a\",\"b\"]}"));
    ((JSONArray) f.get(List.of("args"))).remove(0);
    assertEquals(2, f.original.getJSONArray("args").length());
    assertEquals(1, f.draft.getJSONArray("args").length());
  }
}
