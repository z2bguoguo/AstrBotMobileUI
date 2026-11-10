package io.github.astrbot.mobile.core;

import java.text.NumberFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Unit and timezone formatting without guessing units for unknown server fields. */
public final class StatusFormat {
  public static String format(String kind, Object value, boolean chinese, ZoneId zone) {
    if (value == null || value == org.json.JSONObject.NULL) return "—";
    Locale locale = chinese ? Locale.SIMPLIFIED_CHINESE : Locale.ENGLISH;
    if (kind.equals("timestamp")) {
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
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", locale)
            .withZone(zone)
            .format(time);
      } catch (Exception e) {
        return value.toString();
      }
    }
    if (value instanceof Boolean)
      return chinese ? ((Boolean) value ? "是" : "否") : ((Boolean) value ? "Yes" : "No");
    if (!(value instanceof Number)) return value.toString();
    double n = ((Number) value).doubleValue();
    if (!Double.isFinite(n)) return "—";
    if (kind.equals("percent")) return String.format(locale, "%.1f%%", n);
    if (kind.equals("duration")) {
      if (n < 0) return "—";
      long seconds = (long) n,
          days = seconds / 86400,
          hours = (seconds % 86400) / 3600,
          minutes = (seconds % 3600) / 60;
      long remaining = seconds % 60;
      if (chinese)
        return (days > 0 ? days + " 天 " : "")
            + (hours > 0 || days > 0 ? hours + " 小时 " : "")
            + (minutes > 0 || hours > 0 || days > 0 ? minutes + " 分 " : "")
            + remaining
            + " 秒";
      return (days > 0 ? days + "d " : "")
          + (hours > 0 || days > 0 ? hours + "h " : "")
          + (minutes > 0 || hours > 0 || days > 0 ? minutes + "m " : "")
          + remaining
          + "s";
    }
    if (kind.equals("bytes")) {
      if (n < 0) return "—";
      String[] units = {"B", "KiB", "MiB", "GiB", "TiB", "PiB"};
      int unit = 0;
      while (n >= 1024 && unit < units.length - 1) {
        n /= 1024;
        unit++;
      }
      NumberFormat f = NumberFormat.getNumberInstance(locale);
      f.setMaximumFractionDigits(unit == 0 ? 0 : 2);
      return f.format(n) + " " + units[unit];
    }
    NumberFormat f = NumberFormat.getNumberInstance(locale);
    f.setMaximumFractionDigits(3);
    return f.format(value);
  }

  private StatusFormat() {}
}
