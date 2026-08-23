# React Web

Local-first Jaspr control plane for React server telemetry, heatmaps, incidents, actions, and console access. Server profiles and credentials stay in the browser; Firebase Hosting serves only the static client. The WebSocket relay is a separate service and is not deployed by the Firebase workflow.

## Local development

React Web requires Dart 3.10 or newer and the pinned `arcane_jaspr` checkout at `.deps/arcane_jaspr`. A symlink to a local clone also works.

```bash
git clone https://github.com/ArcaneArts/arcane_jaspr .deps/arcane_jaspr
dart pub get
dart analyze
dart test
dart run jaspr_cli:jaspr serve
```

The relay has an independent Dart package and validation gate:

```bash
cd relay
dart pub get
dart analyze
dart test
dart run bin/relay.dart
```

Production client output is written to `build/jaspr/` by `dart run jaspr_cli:jaspr build`.

## Server connections

Run `/react web pair <label> [role]`, then click the in-game copy action or copy the raw RCT2 value from the server console. React Web validates the server public key and full fingerprint before storing the profile locally in that browser. Multiple profiles can use a direct HTTPS endpoint, the outbound WebSocket relay, or both with automatic failover.

The hosted client runs over HTTPS, so browsers block direct access to React's plain-HTTP listener as mixed content. Production servers must either advertise an HTTPS reverse-proxy URL or enable React's outbound relay with a deployed `wss://` endpoint. Set the relay's `ALLOWED_APP_ORIGINS` to `https://react.volmitsoftware.com`.

## Languages

The top-right language menu switches the complete client-owned interface at
runtime. React Web supports 18 locales in `web/languages/`, persists the choice
as `reactor.locale`, and starts with the saved choice, then the browser locale,
then `REACTOR_LANGUAGE`, then English. A failed or superseded catalog request
keeps the last complete language snapshot and does not change the saved choice.

Each switch updates the document language, direction, title, description
metadata, and install manifest. Hebrew uses right-to-left layout; logs,
commands, pairing codes, identifiers, heatmaps, and charts remain left-to-right
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

## Firebase Hosting

The `react-web-plugin` Firebase project and Hosting site serve [react.volmitsoftware.com](https://react.volmitsoftware.com/). On pushes to `master` that change `react-web/`, `.github/workflows/react-web-firebase-hosting.yml` analyzes and tests both packages, builds the Jaspr client, deploys Hosting, and confirms that the custom domain serves the exact client bundle produced by the job.

The workflow requires the repository or production-environment secret `FIREBASE_SERVICE_ACCOUNT_REACT_WEB_PLUGIN`, containing a Firebase service-account JSON document authorized to deploy Hosting for `react-web-plugin`. Local `service-account.json` is ignored by Git and is not read by the workflow.
