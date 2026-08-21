library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr_test/server_test.dart';

import 'package:react_web/screen/metrics_explorer.dart';
import 'package:react_web/state/connection_manager.dart';
import 'package:react_web/state/server_scope.dart';

const ShadcnStylesheet _sheet = ShadcnStylesheet(theme: ShadcnTheme.midnight);

Widget _wrap(ConnState state) => ArcaneThemeProvider(
  stylesheet: _sheet,
  child: ServerScope(
    snapshot: null,
    state: state,
    child: const MetricsExplorerScreen(),
  ),
);

void main() {
  group('MetricsExplorerScreen missing snapshot states', () {
    testServer('offline state reports reconnecting without another notice', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_wrap(ConnState.offline));

      final DocumentResponse response = await tester.request('/');
      expect(response.statusCode, equals(200));
      expect(response.body, contains('No telemetry snapshot'));
      expect(response.body, contains('after React reconnects'));
      expect(response.body, isNot(contains('reactor-notice')));
      expect(response.body, isNot(contains('server is connected')));
    });

    testServer('degraded state reports recovery without another notice', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(_wrap(ConnState.degraded));

      final DocumentResponse response = await tester.request('/');
      expect(response.statusCode, equals(200));
      expect(response.body, contains('No telemetry snapshot'));
      expect(response.body, contains('after the connection recovers'));
      expect(response.body, isNot(contains('reactor-notice')));
      expect(response.body, isNot(contains('server is connected')));
    });
  });
}
