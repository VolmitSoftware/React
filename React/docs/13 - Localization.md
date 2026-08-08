# Localization

React uses a typed code-owned English catalog, optional bundled locale overlays, and an optional server-local override. Locale changes and override edits can hotload without restarting React.

## Server locale selection

- Global config key: `language` (default `en_US`) on `ReactConfiguration`.
- Missing keys fall back from the selected bundle to code-owned English.
- Optional overrides: `plugins/React/languages/overrides/<locale>.toml` — only listed keys are replaced, and the filename must exactly match the configured locale id.
- If no bundle exists for a valid locale id, React warns and uses code-owned English plus any matching local override.

## Bundled locales

Bundled locale ids are `de_DE`, `es_ES`, `fi_FI`, `fr_FR`, `he_IL`, `it_IT`, `ja-JP`, `ko_KR`, `lt_LT`, `nl_NL`, `pl_PL`, `pt_PT`, `ru_RU`, `tr_TR`, `vi_VI`, `zh_CN`, and `zh_TW`. Locale ids accept letters, digits, `_`, and `-`; Japanese intentionally uses `ja-JP`.

## Catalogs

Server messages are typed Java catalogs under `art.arcane.react.localization.catalog` (for example command, runtime, action, and config messages). Feature/tweak display strings and `@ConfigDoc` English are separate from player-facing command chat where catalogs are used.

## Validation

An overlay may contain nested TOML string values or arrays of strings and is limited to 2 MiB. Templates use strict MiniMessage; message placeholders cannot appear inside MiniMessage tags. A null value, non-string scalar, invalid array member, invalid template, invalid placeholder placement, oversized file, or invalid locale name rejects the reload.

Hotload validates the complete candidate before swapping it in. On rejection React keeps the current active locale, reports up to 12 validation errors, and prints the underlying failure stack trace when present. Missing translated keys are warnings and resolve through the selected bundle and then code-owned English.

Localization tests and completeness gates live in the React test suites. Add new server-visible strings to the typed English catalog; locale overlays may omit them and use fallback.
