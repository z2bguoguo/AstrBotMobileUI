(function () {
  if (window.__astrbotMobileBridge) return;
  window.__astrbotMobileBridge = true;
  let port = null;
  const queue = [];
  window.addEventListener('message', function (event) {
    if (event.source !== window || event.origin !== window.location.origin) return;
    if (event.data === 'astrbot-mobile-port' && event.ports && event.ports[0]) {
      port = event.ports[0];
      port.onmessage = function (incoming) {
        try { window.postMessage(JSON.parse(incoming.data), window.location.origin); } catch (_) {}
      };
      queue.splice(0).forEach(function (message) { port.postMessage(message); });
      return;
    }
    const request = event.data;
    if (!request || request.channel !== 'astrbot-plugin-page' || request.kind !== 'request') return;
    const message = JSON.stringify({ requestId: request.requestId, action: request.action, endpoint: request.endpoint, params: request.params, body: request.body });
    if (port) port.postMessage(message); else queue.push(message);
  });
})();
