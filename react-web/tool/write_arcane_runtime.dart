// Writes arcane_jaspr's interaction runtime to web/arcane-runtime.js. The
// CSP allows scripts from this origin only, so the page loads the runtime as
// a file (`<script defer>`) instead of the inline script ArcaneApp would add.
// Run after every arcane_jaspr update:
//
//   dart run tool/write_arcane_runtime.dart
import 'dart:io';

import 'package:arcane_jaspr/arcane_jaspr.dart' show ArcaneScripts;

void main() {
  final File out = File('web/arcane-runtime.js');
  out.writeAsStringSync(ArcaneScripts.all);
  stdout.writeln('${out.path}: ${ArcaneScripts.all.length} characters');
}
