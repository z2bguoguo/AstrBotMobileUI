package io.github.astrbot.mobile.core;

import java.io.*;
import java.util.Map;

/** Same-origin authenticated streaming is separate from bounded JSON requests. */
public interface StreamTransport extends Transport {
  final class Stream implements AutoCloseable {
    public final int status;
    public final String contentType;
    public final InputStream body;
    private final Runnable disconnect;

    public Stream(int status, String contentType, InputStream body, Runnable disconnect) {
      this.status = status;
      this.contentType = contentType;
      this.body = body;
      this.disconnect = disconnect;
    }

    @Override
    public void close() {
      disconnect.run();
      try {
        if (body != null) body.close();
      } catch (IOException ignored) {
      }
    }
  }

  Stream openStream(String url, Map<String, String> headers) throws IOException;
}
