package io.github.astrbot.mobile.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.json.*;

/** Read-only polling/SSE primitive. No page layout, field semantics or mutation retries. */
public final class ReadWatch implements AutoCloseable {
  private final ApiClient client;
  private final JSONObject plan;
  private final Consumer<JSONObject> sink;
  private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
  private volatile boolean closed;
  private volatile StreamTransport.Stream active;
  private volatile String lastId;
  private final List<JSONObject> queue = new ArrayList<>();
  private String resolved = "";
  private int failures;

  public ReadWatch(ApiClient client, JSONObject plan, Consumer<JSONObject> sink) {
    this.client = client.forkReads();
    this.plan = plan;
    this.sink = sink;
    this.lastId = plan.optString("lastEventId");
  }

  public void start() {
    if (plan.optString("mode").equals("sse")) {
      executor.scheduleWithFixedDelay(this::flush, 250, 250, TimeUnit.MILLISECONDS);
      executor.execute(this::stream);
    } else executor.execute(this::poll);
  }

  public String lastEventId() {
    return lastId;
  }

  private List<String> paths() throws JSONException {
    if (!resolved.isEmpty()) return List.of(resolved);
    JSONArray paths = plan.getJSONArray("paths");
    List<String> result = new ArrayList<>();
    if (paths.length() < 1 || paths.length() > 3)
      throw new IllegalArgumentException("Invalid read fallback list");
    for (int i = 0; i < paths.length(); i++) result.add(paths.getString(i));
    return result;
  }

  private void emit(JSONObject event) {
    if (!closed) sink.accept(event);
  }

  private void failed(Exception error, boolean retry) {
    int status =
        error instanceof ApiClient.ApiException ? ((ApiClient.ApiException) error).status : 0;
    boolean terminal = status >= 400 && status < 500 && status != 408 && status != 429;
    emit(
        CoreRuntime.args(
            "kind",
            "error",
            "status",
            status,
            "retrying",
            retry && !terminal,
            "message",
            error.getMessage() == null ? "Connection interrupted" : error.getMessage()));
    if (retry && !terminal && !closed) {
      long delay = Math.min(30, 1L << Math.min(failures++, 5));
      schedule(plan.optString("mode").equals("sse") ? this::stream : this::poll, delay * 1000);
    }
  }

  private void schedule(Runnable task, long millis) {
    if (!closed)
      try {
        executor.schedule(task, millis, TimeUnit.MILLISECONDS);
      } catch (RejectedExecutionException ignored) {
      }
  }

  private void poll() {
    if (closed) return;
    try {
      Object data;
      String message = "";
      if (plan.optString("resource").equals("overview"))
        data = new ManagementApi(client).overview();
      else {
        JSONObject envelope = null;
        for (String path : paths()) {
          Transport.Reply reply = client.request(path, "GET", null, null);
          if ((reply.status == 404 || reply.status == 405)
              && !path.equals(paths().get(paths().size() - 1))) continue;
          envelope = ApiClient.json(reply);
          resolved = path;
          break;
        }
        message = envelope == null ? "" : envelope.optString("message");
        data = envelope == null ? JSONObject.NULL : envelope.opt("data");
        if (data == null || data == JSONObject.NULL) data = envelope;
      }
      failures = 0;
      emit(CoreRuntime.args("kind", "data", "data", data, "message", message, "path", resolved));
      int interval = plan.optInt("intervalMs");
      if (interval > 0) schedule(this::poll, Math.max(1000, Math.min(3600000, interval)));
    } catch (Exception e) {
      failed(e, plan.optInt("intervalMs") > 0);
    }
  }

  private void stream() {
    if (closed) return;
    try {
      List<String> paths = paths();
      for (int i = 0; i < paths.size(); i++) {
        String path = paths.get(i);
        StreamTransport.Stream stream = client.stream(path, lastId);
        active = stream;
        try (stream) {
          if (closed) return;
          if ((stream.status == 404 || stream.status == 405) && i < paths.size() - 1) continue;
          ApiClient.checkHttp(new Transport.Reply(stream.status, new byte[0], stream.contentType));
          if (!stream.contentType.toLowerCase(Locale.ROOT).contains("text/event-stream"))
            throw new IOException(
                "Expected an SSE response; check reverse proxy buffering and routing");
          resolved = path;
          emit(CoreRuntime.args("kind", "connected", "path", path));
          if (stream.body == null) throw new IOException("Empty event stream");
          SseReader.read(
              new InputStreamReader(stream.body, StandardCharsets.UTF_8),
              () -> closed,
              event -> {
                failures = 0;
                synchronized (queue) {
                  if (queue.size() >= 64) queue.remove(0);
                  queue.add(CoreRuntime.args("id", event.id(), "data", event.data()));
                  lastId = event.id();
                }
              });
          flush();
          if (!closed) throw new EOFException("Stream ended");
          return;
        } finally {
          active = null;
        }
      }
    } catch (Exception e) {
      flush();
      failed(e, true);
    }
  }

  private void flush() {
    JSONArray batch;
    synchronized (queue) {
      if (queue.isEmpty()) return;
      batch = new JSONArray(queue);
      queue.clear();
    }
    emit(CoreRuntime.args("kind", "frames", "frames", batch));
  }

  @Override
  public void close() {
    closed = true;
    client.cancelRead();
    StreamTransport.Stream stream = active;
    if (stream != null) stream.close();
    executor.shutdownNow();
    synchronized (queue) {
      queue.clear();
    }
  }
}
