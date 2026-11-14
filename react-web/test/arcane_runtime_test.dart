// The hosted CSP allows scripts from the site only, so the inline runtime
// ArcaneApp would add is blocked. The page loads the same runtime as a file.
import 'dart:io';

import 'package:arcane_jaspr/arcane_jaspr.dart' show ArcaneScripts;
import 'package:test/test.dart';

void main() {
  test('should load the arcane runtime as a deferred file the CSP allows', () {
    final String index = File('web/index.html').readAsStringSync();
    expect(
      index,
      contains(
        '<script defer data-arcane-runtime src="/arcane-runtime.js"></script>',
      ),
    );
    expect(index, isNot(contains('<script>')));
  });

  test('should ship the runtime of the linked arcane_jaspr', () {
    final File runtime = File('web/arcane-runtime.js');
    expect(
      runtime.existsSync(),
      isTrue,
      reason: 'dart run tool/write_arcane_runtime.dart',
    );
    expect(
      runtime.readAsStringSync(),
      ArcaneScripts.all,
      reason: 'dart run tool/write_arcane_runtime.dart',
    );
  });

  test('should not add the inline runtime', () {
    final String app = File('lib/app/reactor_app.dart').readAsStringSync();
    expect(app, contains('includeFallbackScripts: false'));
  });
}
