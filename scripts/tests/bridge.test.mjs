import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';

const script = fs.readFileSync(new URL('../../app/src/main/assets/mobile-bridge.js', import.meta.url), 'utf8');
function setup() {
  const callbacks = [], forwarded = [], replies = [];
  const window = { location: { origin: 'https://bot.example' }, addEventListener: (type, cb) => callbacks.push(cb), postMessage: (data, origin) => replies.push({ data, origin }) };
  const context = vm.createContext({ window });
  vm.runInContext(script, context);
  const port = { postMessage: data => forwarded.push(JSON.parse(data)) };
  const emit = event => callbacks.forEach(cb => cb(event));
  const request = { channel: 'astrbot-plugin-page', kind: 'request', requestId: 'one', action: 'api:get', endpoint: 'config' };
  return { window, context, port, emit, request, forwarded, replies, callbacks };
}
test('WebView native port with null source drains requests queued during page load', () => {
  const s = setup();
  s.emit({ source: s.window, origin: s.window.location.origin, data: s.request });
  assert.equal(s.forwarded.length, 0);
  s.emit({ source: null, origin: '', data: 'astrbot-mobile-port', ports: [s.port] });
  assert.equal(s.forwarded.length, 1);
  assert.equal(s.forwarded[0].requestId, 'one');
});
test('native replies reach the server plugin bridge on the same origin', () => {
  const s = setup();
  s.emit({ source: null, origin: 'null', data: 'astrbot-mobile-port', ports: [s.port] });
  s.port.onmessage({ data: JSON.stringify({ channel: 'astrbot-plugin-page', kind: 'response', ok: true, data: 42 }) });
  assert.equal(s.replies[0].origin, s.window.location.origin);
  assert.equal(s.replies[0].data.data, 42);
});
test('cross-origin and child-window requests are not forwarded', () => {
  const s = setup();
  s.emit({ source: null, origin: '', data: 'astrbot-mobile-port', ports: [s.port] });
  s.emit({ source: s.window, origin: 'https://evil.example', data: s.request });
  s.emit({ source: {}, origin: s.window.location.origin, data: s.request });
  assert.equal(s.forwarded.length, 0);
});
test('port cannot be replaced and repeated initialization remains idempotent', () => {
  const s = setup();
  s.emit({ source: null, origin: '', data: 'astrbot-mobile-port', ports: [s.port] });
  s.emit({ source: null, origin: '', data: 'astrbot-mobile-port', ports: [{ postMessage() { throw Error('replaced'); } }] });
  vm.runInContext(script, s.context);
  s.emit({ source: s.window, origin: s.window.location.origin, data: s.request });
  assert.equal(s.forwarded.length, 1);
  assert.equal(s.callbacks.length, 1);
});
