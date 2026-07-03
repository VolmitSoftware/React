library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr/jaspr.dart' show Component;
import 'package:jaspr_test/server_test.dart';
import 'package:test/test.dart';

import 'package:reactor/model/heatmap.dart';
import 'package:reactor/service/react_client.dart';
import 'package:reactor/state/heatmap_scope.dart';

const String _kMarker = 'heatmap-client-present';

class _FakeHeatmapClient implements IHeatmapClient {
  @override
  Future<List<HeatmapSummary>> heatmaps() async => <HeatmapSummary>[];

  @override
  Future<HeatmapGrid> heatmap(String id,
          {String? world, int? centerX, int? centerZ, int? radius}) async =>
      throw UnimplementedError();
}

class _Probe extends StatelessWidget {
  const _Probe();

  @override
  Widget build(BuildContext context) {
    final HeatmapScope? scope = HeatmapScope.of(context);
    if (scope?.client != null) {
      return Component.text(_kMarker);
    }
    return Component.fragment(const <Widget>[]);
  }
}

const ShadcnStylesheet _sheet = ShadcnStylesheet(theme: ShadcnTheme.midnight);

Widget _wrap(Widget child) =>
    ArcaneThemeProvider(stylesheet: _sheet, child: child);

void main() {
  group('HeatmapScope', () {
    testServer(
      'probe renders marker when client is non-null',
      (ServerTester tester) async {
        tester.pumpComponent(
          _wrap(
            HeatmapScope(
              client: _FakeHeatmapClient(),
              child: const _Probe(),
            ),
          ),
        );
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains(_kMarker),
          isTrue,
          reason: 'marker must appear when HeatmapScope carries a non-null client',
        );
      },
    );

    testServer(
      'probe does not render marker when client is null',
      (ServerTester tester) async {
        tester.pumpComponent(
          _wrap(
            HeatmapScope(
              client: null,
              child: const _Probe(),
            ),
          ),
        );
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains(_kMarker),
          isFalse,
          reason: 'marker must not appear when HeatmapScope carries a null client',
        );
      },
    );
  });
}
