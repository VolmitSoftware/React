# Translating React Web

Every supported language has one UTF-8 JSON catalog in this directory. The
English source template is `en_US.json`; copy it, keep every key unchanged, and
translate only the values. Language files are intentionally flat so they work
well with translation platforms and ordinary JSON editors.

Placeholders such as `{server}`, `{count}`, and `{action}` must remain exactly
the same, although they may move within a sentence. Keep product names,
commands, URLs, sampler IDs, and protocol tokens such as `React`, `RCT2`, `TPS`,
`MSPT`, `HTTP`, and `WebSocket` unchanged.

Use the key as the translation context when an English label is ambiguous:

- `screen.config_editor.preset.light` is the lightweight performance preset;
  `screen.settings.light_theme` is the bright visual theme.
- `screen.logs.resume`, `screen.logs.pause`, and `screen.logs.clear` are actions
  on log output, while `screen.logs.stream` is the telemetry data stream.
- `screen.add_server.pair` is the verb for securely associating a server;
  `screen.add_server.port` is a network port and `handshake` is the networking
  protocol term.
- `chart.sample` and `screen.metrics.samples` are telemetry measurements.
- `screen.world_overrides.title` means settings applied to individual
  Minecraft worlds, and `screen.heatmaps.event_impact_pie` means a pie chart.

After a code change, refresh the English template and validate all catalogs:

```bash
dart run tool/locale_catalog.dart --write-template
dart run tool/locale_catalog.dart --check
```

The template command also refreshes the localized install manifests in
`../manifests/`. After changing only translations, regenerate those derived
files with `dart run tool/locale_catalog.dart --write-manifests`, then run the
check command.

The validator rejects missing or unknown keys, non-string values, malformed
JSON, placeholder drift, and stale install manifests. The application will not
install a catalog that fails the same runtime validation.
