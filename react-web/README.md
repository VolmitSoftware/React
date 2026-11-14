# React Web

Local-first Jaspr control plane for React server telemetry, heatmaps, incidents, actions, and console access. Server profiles and credentials stay in the browser; Firebase Hosting serves only the static client. The WebSocket relay is a separate service and is not deployed by the Firebase workflow.

## Local development

React Web uses Dart 3.13.5 and the `arcane_jaspr` revision pinned in `.github/workflows/react-web-firebase-hosting.yml`, checked out at `.deps/arcane_jaspr`. That checkout supplies the core and the shadcn theme; React Web runs shadcn Midnight with Geist (`lib/theme/reactor_stylesheet.dart`). A symlink to a local clone at the same revision also works.

```bash
git clone https://github.com/TimeZone-LLC/arcane_jaspr .deps/arcane_jaspr
ARCANE_JASPR_REF="$(sed -n 's/^  ARCANE_JASPR_REF: //p' ../.github/workflows/react-web-firebase-hosting.yml)"
git -C .deps/arcane_jaspr checkout "$ARCANE_JASPR_REF"
dart pub get --enforce-lockfile
dart analyze
dart test
dart run tool/locale_catalog.dart --check
dart run tool/write_arcane_runtime.dart   # after an arcane_jaspr update; the CSP blocks the inline runtime
dart run jaspr_cli:jaspr build
python3 tool/serve_site.py --port 8820
```

`tool/serve_site.py` serves `build/jaspr/` the way Firebase Hosting does: any address without a file gets `index.html`, so deep links such as `/server/<id>/console` load.

The relay has an independent Dart package and validation gate:

```bash
cd relay
dart pub get
dart analyze
dart test
dart run bin/relay.dart
```

## Trying it without a server

`tool/fake_server.dart` runs a fake fleet of React servers on one port, each under its own path, with live metrics, logs, actions and pairing links:

```bash
dart run tool/fake_server.dart --port=8821 --origin=http://127.0.0.1:8820 --servers=5
```

It prints a pairing link per server; open one, or paste it into **Pairing link** on Home. Each link works once. `GET /__fake/servers` lists the servers with a new link each, and `POST /__fake/pair?server=s2&role=viewer` makes another (the other routes are listed in `tool/fake_server/fleet_control.dart`).

## Browser checks

The `test/*_browser.cjs` scripts drive the built app with Playwright against the fake fleet (`REACT_WEB_URL`, `REACT_FAKE_URL` and `PLAYWRIGHT_MODULE` set; `REACT_MODE=simple` walks Simple mode). Each walks every page, tab, dialog and sheet at 1600, 768 and 390 px and ends at 0 problems:

- `button_uniformity_browser.cjs`: button sizes, one height per row, primary last, the 8 px gap, phone footers stacked;
- `nested_cards_browser.cjs`: no card inside a card;
- `preview_fit_browser.cjs`: charts and the map fit their card without an inner scrollbar;
- `page_overflow_browser.cjs`: no sideways scrolling;
- `layout_rules_browser.cjs`: one control height per toolbar (default in page toolbars, sm in card heads and rows), no select text cut without an ellipsis, no word broken in a list cell, no tile unit that repeats its label, one tab stop per chart, every form control named and every field error linked to its control, and a Console page that fits the window.

`pairing_flow_browser.cjs`, `pairing_link_browser.cjs` (the card, Remember this browser, each failure, Pairing ended, Forget server), `live_data_browser.cjs` (fake fleet with `--idle-timeout=5`), `toolbar_browser.cjs` and `load_timing_browser.cjs` cover pairing, live updates, the top bar and load times.

## Server connections

Run `/react web pair` in game or in the console. It prints a link that works once, within 10 minutes; `role=viewer` or `role=admin` changes what it allows (the default is operator). Opening the link shows one card with the server's name, until when the link works and what React Web may do there. **Browser name** is how `/react web tokens` lists this browser, and **Remember this browser** (on by default) keeps the pairing in every tab; off, it ends with the tab. Press **Pair**. A link can also be pasted into **Pairing link** on Home or into **Pair a server** (server switcher, server group, Settings > Servers). Home lists every paired server with its state, players, TPS and open incidents.

A pairing lasts as long as it is used: it ends after `tokenDays` (7 days) without use. When the server revokes it or it runs out, the server's pages and its row on Home say "Pairing ended." with a **New link** field.

The link carries every path the server offers: its own listener and the outbound relay. React Web uses whichever answers and switches between them. Before a pairing secret or token goes through the relay, the server signs a challenge with its key and React Web checks it against the key in the link. The page's origin must be in `allowedOrigins` in `plugins/React/web.toml`; a refused origin says so and names the key. Fresh installations listen on `::` port `9696` (`listenAddress = "0.0.0.0"` on IPv4-only hosts); `web.toml` reloads when saved.

The hosted client runs over HTTPS, so browsers block direct access to React's plain-HTTP listener as mixed content, and may ask before an HTTPS page reaches a local network address. Production servers must either advertise an HTTPS reverse-proxy URL (`advertisedUrl`) or enable React's outbound relay with a deployed `wss://` endpoint. Set the relay's `ALLOWED_APP_ORIGINS` to `https://react.volmitsoftware.com`.

## Languages

Select language in the top bar's "..." menu (or Settings > General) switches the complete client-owned interface at
runtime. React Web supports 18 locales in `web/languages/`, persists the choice
as `reactor.locale`, and starts with the saved choice, then the browser locale,
then `REACTOR_LANGUAGE`, then English. A failed or superseded catalog request
keeps the last complete language snapshot and does not change the saved choice.

Each switch updates the document language, direction, title, description
metadata, and install manifest. Hebrew uses right-to-left layout; logs,
commands, pairing links, identifiers, heatmaps, and charts remain left-to-right
for readability. Translation instructions and the catalog and generated-
manifest validator are in
[`web/languages/README.md`](web/languages/README.md).

`web/reactor-language.json` is an optional deployment overlay for the locale
selected by `REACTOR_LANGUAGE`. It is merged after that locale's bundled
catalog, so it can override typed keys without rebuilding Dart. Other language
choices use only their own bundle, preventing a configured overlay from mixing
languages. Malformed bundles or overlays are rejected atomically.

Action descriptions, control names, configuration labels, sampler names, log
content, and incident details received from a React server are server-provided
data. React Web translates all client-owned framing around those values, while
the server controls the language of the values themselves.

The browser-language dropdown is independent from React's server language. The global configuration editor renders `main.language` as a supported-locale dropdown; saving it updates `react.toml`, reloads live in-game messages, and replaces the editor state with the authoritative server response.

## Environment diagnostics

Performance > Host polls typed disk and network counters every five seconds while it is open. Disk read/write and network receive/send charts retain a bounded local rate history with hover and keyboard details, while mounted-volume capacity bars and device/interface summaries expose the current totals.

## Firebase Hosting

The `react-web-plugin` Firebase project and Hosting site serve [react.volmitsoftware.com](https://react.volmitsoftware.com/). On pushes to `master` that change `react-web/`, `.github/workflows/react-web-firebase-hosting.yml` analyzes and tests both packages, builds the Jaspr client, deploys Hosting, and confirms that the custom domain serves the exact client bundle produced by the job.

The workflow requires the repository or production-environment secret `SERVICE_ACCOUNT`, containing a Firebase service-account JSON document authorized to deploy Hosting for `react-web-plugin`. Local `service-account.json` is ignored by Git and is not read by the workflow.
