library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr_test/server_test.dart';
import 'package:react_web/theme/reactor_stylesheet.dart';

void main() {
  testServer('Midnight shell renders Geist without remote fonts', (
    ServerTester tester,
  ) async {
    tester.pumpComponent(
      const ArcaneApp(stylesheet: ReactorStylesheet(), home: Text('React Web')),
    );

    final DocumentResponse response = await tester.request('/');
    expect(response.statusCode, 200);
    expect(response.body, contains('arcane-theme-shadcn'));
    expect(response.body, contains('React Web'));
    expect(response.body, contains("font-family: 'Geist'"));
    expect(response.body, contains('/assets/fonts/geist/Geist-Regular.woff2'));
    expect(response.body, isNot(contains('fonts.googleapis.com')));
  });
}
