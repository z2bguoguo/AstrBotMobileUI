package io.github.astrbot.mobile.core;

import java.time.*;
import org.json.*;

/** Only calendar/timezone access stays native. All display formatting is script-owned. */
public final class StatusFormat {
  public static String format(String kind, Object value, boolean chinese, ZoneId zone) {
    return format(kind, value, chinese, zone, "");
  }

  public static String format(
      String kind, Object value, boolean chinese, ZoneId zone, String pattern) {
    JSONObject parts = null;
    if (kind.equals("timestamp") && value != null && value != JSONObject.NULL) {
      try {
        Instant time;
        if (value instanceof Number || value.toString().matches("[0-9]+(?:\\.[0-9]+)?")) {
          double n = Double.parseDouble(value.toString());
          if (!Double.isFinite(n) || n < 0) return value.toString();
          time = Instant.ofEpochMilli(Math.round(n >= 1_000_000_000_000L ? n : n * 1000));
        } else {
          try {
            time = Instant.parse(value.toString());
          } catch (Exception e) {
            time = OffsetDateTime.parse(value.toString()).toInstant();
          }
        }
        ZonedDateTime t = time.atZone(zone);
        parts =
            CoreRuntime.args(
                "year",
                t.getYear(),
                "month",
                t.getMonthValue(),
                "day",
                t.getDayOfMonth(),
                "hour",
                t.getHour(),
                "minute",
                t.getMinute(),
                "second",
                t.getSecond());
      } catch (Exception e) {
        return value.toString();
      }
    }
    if (value instanceof Number && !Double.isFinite(((Number) value).doubleValue())) return "—";
    return CoreRuntime.text(
        "status.format",
        CoreRuntime.args(
            "kind",
            kind,
            "value",
            value == null ? JSONObject.NULL : value,
            "chinese",
            chinese,
            "parts",
            parts == null ? JSONObject.NULL : parts,
            "pattern",
            pattern));
  }

  private StatusFormat() {}
}
