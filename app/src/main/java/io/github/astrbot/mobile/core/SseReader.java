package io.github.astrbot.mobile.core;

import java.io.*;
import java.util.function.*;

/** Bounded SSE parser; preserves multiline UTF-8 payloads and ignores heartbeats. */
public final class SseReader {
  public record Event(String id, String data) {}

  public static void read(Reader input, BooleanSupplier cancelled, Consumer<Event> sink)
      throws IOException {
    BufferedReader reader = new BufferedReader(input);
    StringBuilder line = new StringBuilder(), data = new StringBuilder();
    String id = "";
    boolean first = true;
    int value;
    while (!cancelled.getAsBoolean() && (value = reader.read()) != -1) {
      char c = (char) value;
      if (first) {
        first = false;
        if (c == '\ufeff') continue;
      }
      if (c != '\n' && c != '\r') {
        line.append(c);
        if (line.length() > 65536) throw new IOException("SSE line exceeds limit");
        continue;
      }
      if (c == '\r') {
        reader.mark(1);
        if (reader.read() != '\n') reader.reset();
      }
      String text = line.toString();
      line.setLength(0);
      if (text.isEmpty()) {
        if (data.length() > 0) {
          sink.accept(new Event(id, data.substring(0, data.length() - 1)));
          data.setLength(0);
        }
        continue;
      }
      if (text.startsWith(":")) continue;
      int colon = text.indexOf(':');
      String key = colon < 0 ? text : text.substring(0, colon),
          part = colon < 0 ? "" : text.substring(colon + 1);
      if (part.startsWith(" ")) part = part.substring(1);
      if (key.equals("id") && !part.contains("\0") && part.length() <= 256) id = part;
      if (key.equals("data")) {
        data.append(part).append('\n');
        if (data.length() > 65536) throw new IOException("SSE event exceeds limit");
      }
    }
  }

  private SseReader() {}
}
