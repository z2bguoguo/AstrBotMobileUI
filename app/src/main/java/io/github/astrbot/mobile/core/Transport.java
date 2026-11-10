package io.github.astrbot.mobile.core;

import java.io.IOException;
import java.util.Map;

public interface Transport {
  final class Reply {
    public final int status;
    public final byte[] body;
    public final String contentType;

    public Reply(int status, byte[] body, String contentType) {
      this.status = status;
      this.body = body;
      this.contentType = contentType;
    }

    public String text() {
      return new String(body, java.nio.charset.StandardCharsets.UTF_8);
    }
  }

  Reply send(String url, String method, Map<String, String> headers, byte[] body)
      throws IOException;
}
