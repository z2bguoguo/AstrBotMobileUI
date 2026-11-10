package io.github.astrbot.mobile.core;

import static org.junit.Assert.*;

import java.time.ZoneId;
import org.junit.Test;

public class StatusFormatTest {
  private String f(String kind, Object value) {
    return StatusFormat.format(kind, value, true, ZoneId.of("UTC"));
  }

  @Test
  public void combinesDurationAndHandlesZero() {
    assertEquals("0 秒", f("duration", 0));
    assertEquals("1 小时 1 分 1 秒", f("duration", 3661));
    assertEquals("1 天 1 小时 1 分 1 秒", f("duration", 90061));
    assertEquals("1d 1h 1m 1s", StatusFormat.format("duration", 90061, false, ZoneId.of("UTC")));
  }

  @Test
  public void formatsCapacityAndPercentWithoutChangingMeaning() {
    assertEquals("660 MiB", f("bytes", 660L * 1048576));
    assertEquals("1.66 GiB", f("bytes", 1699L * 1048576));
    assertEquals("0 B", f("bytes", 0));
    assertEquals("3.0%", f("percent", 3));
    assertEquals("150.0%", f("percent", 150));
  }

  @Test
  public void formatsEpochSecondsMillisecondsAndExplicitTimezones() {
    assertEquals("1970-01-01 00:00:00", f("timestamp", 0));
    assertEquals(f("timestamp", 1794155493L), f("timestamp", 1794155493000L));
    assertEquals(f("timestamp", 1794155493L), f("timestamp", "1794155493"));
    assertEquals(
        "1970-01-01 08:00:00",
        StatusFormat.format("timestamp", 0, true, ZoneId.of("Asia/Shanghai")));
    assertEquals("2026-11-10 00:00:00", f("timestamp", "2026-11-10T08:00:00+08:00"));
  }

  @Test
  public void missingAndInvalidMetricsNeverBecomeFakeZeroUsage() {
    assertEquals("—", f("bytes", null));
    assertEquals("—", f("percent", Double.NaN));
    assertEquals("—", f("duration", -1));
    assertEquals("—", f("bytes", -1));
    assertEquals("not a date", f("timestamp", "not a date"));
  }

  @Test
  public void countsAndBooleanStatesAreReadable() {
    assertEquals("1,234,567", f("count", 1234567));
    assertEquals("是", f("text", true));
    assertEquals("No", StatusFormat.format("text", false, false, ZoneId.of("UTC")));
  }
}
