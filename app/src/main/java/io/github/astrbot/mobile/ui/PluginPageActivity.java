package io.github.astrbot.mobile.ui;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.*;
import android.widget.*;
import java.net.URI;

/** No native bridge, no credential injection, no TLS bypass, same-instance navigation only. */
public final class PluginPageActivity extends Activity {
  private WebView web;
  private String sessionId;
  private final java.util.concurrent.ExecutorService worker =
      java.util.concurrent.Executors.newSingleThreadExecutor();
  private WebMessagePort channel;

  private void attachBridge(
      io.github.astrbot.mobile.core.ApiClient client, String plugin, String base) {
    try {
      String script =
          new String(
              io.github.astrbot.mobile.core.HttpTransport.readLimited(
                  getAssets().open("mobile-bridge.js"), 65536),
              java.nio.charset.StandardCharsets.UTF_8);
      web.evaluateJavascript(
          script,
          ignored -> {
            WebMessagePort[] ports = web.createWebMessageChannel();
            channel = ports[0];
            channel.setWebMessageCallback(
                new WebMessagePort.WebMessageCallback() {
                  @Override
                  public void onMessage(WebMessagePort port, WebMessage message) {
                    String raw = message.getData();
                    if (raw == null || raw.length() > 1024 * 1024) return;
                    worker.submit(
                        () -> {
                          org.json.JSONObject reply = new org.json.JSONObject();
                          try {
                            org.json.JSONObject request = new org.json.JSONObject(raw);
                            reply
                                .put("channel", "astrbot-plugin-page")
                                .put("kind", "response")
                                .put("requestId", request.optString("requestId"));
                            String action = request.optString("action");
                            if (!action.equals("api:get") && !action.equals("api:post"))
                              throw new IllegalArgumentException(
                                  "此移动版本的 HTML bridge 仅支持 apiGet/apiPost；文件和流式操作请使用 WebUI");
                            String path =
                                io.github.astrbot.mobile.core.PluginScope.endpoint(
                                    plugin, request.optString("endpoint"), client.modern);
                            org.json.JSONObject params = request.optJSONObject("params");
                            if (action.equals("api:get") && params != null)
                              path =
                                  new io.github.astrbot.mobile.core.Endpoint(
                                          path, "GET", new org.json.JSONObject())
                                      .resolve(new org.json.JSONObject(), params);
                            byte[] body =
                                action.equals("api:post")
                                    ? (request.optJSONObject("body") == null
                                            ? "{}"
                                            : request.getJSONObject("body").toString())
                                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)
                                    : null;
                            org.json.JSONObject response =
                                io.github.astrbot.mobile.core.ApiClient.json(
                                    client.request(
                                        path,
                                        body == null ? "GET" : "POST",
                                        body,
                                        body == null ? null : "application/json"));
                            reply
                                .put("ok", true)
                                .put(
                                    "data", response.has("data") ? response.get("data") : response);
                          } catch (Exception e) {
                            try {
                              reply.put("ok", false).put("error", e.getMessage());
                            } catch (org.json.JSONException ignored) {
                            }
                          }
                          runOnUiThread(
                              () -> {
                                if (!isFinishing() && channel == port)
                                  port.postMessage(new WebMessage(reply.toString()));
                              });
                        });
                  }
                });
            java.net.URI origin = java.net.URI.create(base);
            web.postWebMessage(
                new WebMessage("astrbot-mobile-port", new WebMessagePort[] {ports[1]}),
                android.net.Uri.parse(origin.getScheme() + "://" + origin.getRawAuthority()));
          });
    } catch (Exception ignored) {
      android.widget.Toast.makeText(this, "插件桥接初始化失败", android.widget.Toast.LENGTH_LONG).show();
    }
  }

  public static boolean allowed(String base, String target) {
    try {
      URI a = URI.create(base), b = URI.create(target);
      int ap = a.getPort() < 0 ? (a.getScheme().equals("https") ? 443 : 80) : a.getPort(),
          bp = b.getPort() < 0 ? (b.getScheme().equals("https") ? 443 : 80) : b.getPort();
      return a.getScheme().equals(b.getScheme())
          && a.getHost().equals(b.getHost())
          && ap == bp
          && b.getUserInfo() == null
          && b.getPath().startsWith(a.getPath() + "/api/")
          && !b.getPath().contains("..");
    } catch (Exception e) {
      return false;
    }
  }

  @android.annotation.SuppressLint(
      "SetJavaScriptEnabled") // Required for explicitly opened plugin HTML; requests remain
  // plugin-scoped.
  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    String base = getIntent().getStringExtra("base"), url = getIntent().getStringExtra("url");
    sessionId = getIntent().getStringExtra("session");
    io.github.astrbot.mobile.core.ApiClient client = PluginSessions.get(sessionId);
    String plugin = getIntent().getStringExtra("plugin");
    if (client == null || plugin == null || !allowed(base, url)) {
      finish();
      return;
    }
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setOnApplyWindowInsetsListener(
        (v, i) -> {
          v.setPadding(
              i.getSystemWindowInsetLeft(),
              i.getSystemWindowInsetTop(),
              i.getSystemWindowInsetRight(),
              i.getSystemWindowInsetBottom());
          return i;
        });
    Button close = new Button(this);
    close.setText("‹ 返回原生客户端");
    close.setOnClickListener(v -> finish());
    root.addView(close);
    web = new WebView(this);
    web.getSettings().setJavaScriptEnabled(true);
    web.getSettings().setDomStorageEnabled(true);
    web.getSettings().setAllowFileAccess(false);
    web.getSettings().setAllowContentAccess(false);
    web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
    web.setWebViewClient(
        new WebViewClient() {
          @Override
          public void onPageFinished(WebView view, String loaded) {
            if (allowed(base, loaded)
                && java.net
                    .URI
                    .create(loaded)
                    .getPath()
                    .startsWith(java.net.URI.create(url).getPath()))
              attachBridge(client, plugin, base);
          }

          @Override
          public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
            return !allowed(base, req.getUrl().toString());
          }

          @Override
          public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
            if (req.isForMainFrame() && req.getUrl().toString().equals(url)) {
              try {
                io.github.astrbot.mobile.core.Transport.Reply response =
                    new io.github.astrbot.mobile.core.HttpTransport()
                        .send(url, "GET", java.util.Collections.emptyMap(), null);
                io.github.astrbot.mobile.core.ApiClient.checkHttp(response);
                if (response.contentType.contains("text/html")) {
                  String script =
                      new String(
                          io.github.astrbot.mobile.core.HttpTransport.readLimited(
                              getAssets().open("mobile-bridge.js"), 65536),
                          java.nio.charset.StandardCharsets.UTF_8);
                  String html = response.text();
                  java.util.regex.Matcher head =
                      java.util.regex.Pattern.compile("(?i)<head\\b[^>]*>").matcher(html);
                  if (head.find())
                    html =
                        html.substring(0, head.end())
                            + "<script>"
                            + script
                            + "</script>"
                            + html.substring(head.end());
                  else html = "<script>" + script + "</script>" + html;
                  return new WebResourceResponse(
                      "text/html",
                      "UTF-8",
                      new java.io.ByteArrayInputStream(
                          html.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                }
              } catch (Exception ignored) {
                return new WebResourceResponse(
                    "text/plain",
                    "UTF-8",
                    new java.io.ByteArrayInputStream(
                        "Plugin page unavailable"
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
              }
            }
            // Plugin assets and API calls stay on the selected instance; third-party scripts are
            // refused.
            if (!allowed(base, req.getUrl().toString()))
              return new WebResourceResponse(
                  "text/plain",
                  "UTF-8",
                  403,
                  "Blocked",
                  java.util.Collections.emptyMap(),
                  new java.io.ByteArrayInputStream(new byte[0]));
            return null;
          }
        });
    root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
    setContentView(root);
    web.loadUrl(url);
  }

  @Override
  protected void onDestroy() {
    if (channel != null) {
      channel.close();
      channel = null;
    }
    worker.shutdownNow();
    if (!isChangingConfigurations()) PluginSessions.remove(sessionId);
    if (web != null) {
      web.stopLoading();
      web.clearHistory();
      web.clearCache(true);
      web.destroy();
    }
    super.onDestroy();
  }
}
