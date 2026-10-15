library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_ascii/arcane_jaspr_ascii.dart';
import 'package:jaspr_test/server_test.dart';

void main() {
  testServer('Ascii shell renders without remote fonts', (
    ServerTester tester,
  ) async {
    tester.pumpComponent(
      const ArcaneApp(stylesheet: AsciiStylesheet(), home: Text('React Web')),
    );

    final DocumentResponse response = await tester.request('/');
    expect(response.statusCode, 200);
    expect(response.body, contains('arcane-theme-ascii'));
    expect(response.body, contains('React Web'));
    expect(response.body, isNot(contains('fonts.googleapis.com')));
  });
}
