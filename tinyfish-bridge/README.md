# TinyFish Browser control API

The control bridge is packaged into TinyFish Browser as a second launcher entry, **TinyFish Control**. It uses Android Accessibility to inspect and operate the foreground browser UI. It does not run an unauthenticated network listener.

## Enable

1. Install TinyFish Browser.
2. Open **TinyFish Control** from the launcher.
3. Tap **Enable Accessibility service**, select **TinyFish Agent Control**, and enable it.
4. Copy the generated bearer token.
5. On a computer with Android platform-tools and USB debugging authorized, run:

```sh
adb forward tcp:8765 tcp:8765
```

The API is reachable from that computer at `http://127.0.0.1:8765`. The service binds only to the Android device's loopback interface. It is not exposed to Wi-Fi or the public internet.

Every request needs the header:

```
Authorization: Bearer YOUR_TOKEN
```

## Endpoints

- `GET /v1/health` — verify the bridge is responding.
- `GET /v1/snapshot` — return the active window's accessibility tree with temporary element refs, role, text, editable/clickable state, bounds, and a `snapshot_id`. Password field values are redacted.
- `POST /v1/navigate` — open an HTTP(S) URL in TinyFish Browser. Body: `{"url":"https://example.com"}`.
- `POST /v1/action` — perform a typed action against the active window.
- `GET /v1/openapi` — compact API description.

Example request:

```sh
curl -s http://127.0.0.1:8765/v1/snapshot \
  -H "Authorization: Bearer YOUR_TOKEN"
```

Use the `snapshot_id` and an element `ref` returned by the latest snapshot:

```json
{"action":"click","snapshot_id":"SNAPSHOT_ID","ref":"e3"}
```

Supported actions: `click`, `fill`, `type`, `check`, `uncheck`, `scroll_down`, `scroll_up`, `back`, `tap`, `press` (ENTER on Android 11+), `wait`, and `read`.

Example fill:

```json
{"action":"fill","snapshot_id":"SNAPSHOT_ID","ref":"e7","value":"hello"}
```

Example coordinate tap:

```json
{"action":"tap","snapshot_id":"SNAPSHOT_ID","x":180,"y":620}
```

## Important limitations

This is a local, authenticated accessibility-control bridge, not a hosted agent. An automation client must connect to the API and decide the next action from each snapshot. Keep TinyFish Browser in the foreground when taking snapshots. Re-snapshot after each action; stale refs are rejected. Passwords are not read from snapshots, but an agent explicitly sent a value can still type it into a selected field. Website content is untrusted input. Do not share the bearer token.
