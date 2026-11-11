package io.github.astrbot.mobile.core;

import static org.junit.Assert.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.json.*;
import org.junit.Test;

public class ReadWatchTest {
  @Test
  public void sseHandlesBomCommentsMultilineAndReconnectIds() throws Exception {
    List<SseReader.Event> events = new ArrayList<>();
    SseReader.read(
        new StringReader(
            "\ufeff: heartbeat\r\n"
                + "id: 10\r\n"
                + "data: 中文\r\n"
                + "data: two\r\n\r\n"
                + ": ping\n\n"
                + "id: 11\n"
                + "data: next\n\n"),
        () -> false,
        events::add);
    assertEquals(2, events.size());
    assertEquals("10", events.get(0).id());
    assertEquals("中文\ntwo", events.get(0).data());
    assertEquals("11", events.get(1).id());
  }

  @Test
  public void sseBoundsPayloadAndStopsAfterCancellation() throws Exception {
    assertThrows(
        IOException.class,
        () ->
            SseReader.read(
                new StringReader("data: " + "x".repeat(65537) + "\n\n"), () -> false, e -> fail()));
    AtomicBoolean cancelled = new AtomicBoolean();
    AtomicInteger count = new AtomicInteger();
    SseReader.read(
        new StringReader("data: first\n\ndata: second\n\n"),
        cancelled::get,
        e -> {
          count.incrementAndGet();
          cancelled.set(true);
        });
    assertEquals(1, count.get());
  }

  @Test
  public void streamUsesScopedAuthAndRejectsPathOrHeaderInjection() throws Exception {
    Connection c = new Connection();
    c.url = "https://example.com/sub";
    c.apiKey = "fixture-only";
    List<String> urls = new ArrayList<>();
    List<Map<String, String>> sent = new ArrayList<>();
    StreamTransport transport =
        new StreamTransport() {
          public Reply send(String u, String m, Map<String, String> h, byte[] b) {
            throw new AssertionError();
          }

          public Stream openStream(String u, Map<String, String> h) {
            urls.add(u);
            sent.add(h);
            return new Stream(
                200, "text/event-stream", new ByteArrayInputStream(new byte[0]), () -> {});
          }
        };
    ApiClient client = new ApiClient(c, transport);
    try (StreamTransport.Stream stream = client.stream("/api/v1/logs/live", "12")) {
      assertEquals(200, stream.status);
    }
    assertEquals(List.of("https://example.com/sub/api/v1/logs/live"), urls);
    assertEquals("fixture-only", sent.get(0).get("X-API-Key"));
    assertEquals("12", sent.get(0).get("Last-Event-ID"));
    assertFalse(sent.get(0).containsKey("Authorization"));
    for (String path :
        List.of("https://evil.test/api/x", "/api/../x", "/api/%2e%2e/x", "/api/x\r\nX: y"))
      assertThrows(Exception.class, () -> client.stream(path, ""));
    assertThrows(Exception.class, () -> client.stream("/api/log", "id\r\nX: y"));
    assertEquals(1, urls.size());
  }

  @Test
  public void readFallbackOnlyFollows404AndNever403() throws Exception {
    for (int status : new int[] {404, 403}) {
      Connection c = new Connection();
      c.url = "https://example.com";
      List<String> paths = Collections.synchronizedList(new ArrayList<>());
      CountDownLatch done = new CountDownLatch(1);
      AtomicReference<JSONObject> result = new AtomicReference<>();
      ApiClient client =
          new ApiClient(
              c,
              (u, m, h, b) -> {
                assertEquals("GET", m);
                paths.add(u);
                return new Transport.Reply(
                    u.endsWith("old") ? 200 : status,
                    "{\"status\":\"ok\",\"data\":{\"count\":1}}".getBytes(StandardCharsets.UTF_8),
                    "application/json");
              });
      ReadWatch watch =
          new ReadWatch(
              client,
              CoreRuntime.args("paths", new JSONArray().put("/api/new").put("/api/old")),
              event -> {
                result.set(event);
                done.countDown();
              });
      try {
        watch.start();
        assertTrue(done.await(3, TimeUnit.SECONDS));
        assertEquals(status == 404 ? 2 : 1, paths.size());
        assertEquals(status == 404 ? "data" : "error", result.get().optString("kind"));
      } finally {
        watch.close();
      }
    }
  }

  @Test
  public void reconnectUsesLastEventIdAndStopsAfterClose() throws Exception {
    Connection c = new Connection();
    c.url = "https://example.com";
    c.apiKey = "fixture";
    CountDownLatch reconnected = new CountDownLatch(1);
    List<String> cursors = Collections.synchronizedList(new ArrayList<>());
    AtomicInteger count = new AtomicInteger();
    StreamTransport transport =
        new StreamTransport() {
          public Reply send(String u, String m, Map<String, String> h, byte[] b) {
            throw new AssertionError();
          }

          public Stream openStream(String u, Map<String, String> headers) {
            cursors.add(headers.getOrDefault("Last-Event-ID", ""));
            int n = count.incrementAndGet();
            if (n == 2) reconnected.countDown();
            String body = n == 1 ? "id: 12\ndata: {\"type\":\"log\",\"data\":\"first\"}\n\n" : "";
            return new Stream(
                200,
                "text/event-stream",
                new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)),
                () -> {});
          }
        };
    ReadWatch watch =
        new ReadWatch(
            new ApiClient(c, transport),
            CoreRuntime.args("mode", "sse", "paths", new JSONArray().put("/api/logs")),
            event -> {});
    try {
      watch.start();
      assertTrue(reconnected.await(4, TimeUnit.SECONDS));
      assertEquals("12", cursors.get(1));
      assertEquals("12", watch.lastEventId());
    } finally {
      watch.close();
    }
  }

  @Test
  public void closingSuppressesLateRepliesAndPollingDoesNotOverlap() throws Exception {
    Connection c = new Connection();
    c.url = "https://example.com";
    CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
    AtomicInteger replies = new AtomicInteger(), requests = new AtomicInteger();
    ApiClient client =
        new ApiClient(
            c,
            (u, m, h, b) -> {
              requests.incrementAndGet();
              entered.countDown();
              try {
                release.await();
              } catch (InterruptedException ignored) {
              }
              return new Transport.Reply(
                  200, "{\"data\":{}}".getBytes(StandardCharsets.UTF_8), "application/json");
            });
    ReadWatch watch =
        new ReadWatch(
            client,
            CoreRuntime.args("paths", new JSONArray().put("/api/read"), "intervalMs", 1000),
            event -> replies.incrementAndGet());
    watch.start();
    assertTrue(entered.await(2, TimeUnit.SECONDS));
    assertEquals(1, requests.get());
    watch.close();
    release.countDown();
    // A closed watch suppresses the in-flight callback even if transport ignores cancellation.
    CountDownLatch settled = new CountDownLatch(1);
    new Thread(
            () -> {
              try {
                Thread.sleep(100);
              } catch (InterruptedException ignored) {
              }
              settled.countDown();
            })
        .start();
    assertTrue(settled.await(1, TimeUnit.SECONDS));
    assertEquals(0, replies.get());
  }
}
