library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr_test/server_test.dart';
import 'package:react_web/screen/heatmaps_viewport.dart';
import 'package:react_web/theme/reactor_stylesheet.dart';
import 'package:react_web/widget/heatmap_controls.dart';

const ReactorStylesheet _sheet = ReactorStylesheet();

void main() {
  testServer('each view field is a library field whose label names its box', (
    ServerTester tester,
  ) async {
    tester.pumpComponent(
      ArcaneThemeProvider(
        stylesheet: _sheet,
        child: HeatmapViewportFields(
          viewport: const HeatmapViewport(
            world: 'world',
            centerChunkX: 4,
            centerChunkZ: -2,
            radius: 8,
          ),
          onCommit: (HeatmapViewport _) {},
        ),
      ),
    );
    final String body = (await tester.request('/')).body;
    expect('data-slot="field"'.allMatches(body), hasLength(3));
    for (final String id in <String>[
      'heatmap-centerX',
      'heatmap-centerZ',
      'heatmap-radius',
    ]) {
      expect(body, contains(RegExp('<label\\b[^>]*for="$id"')), reason: id);
      expect(body, contains(RegExp('<input\\b[^>]*id="$id"')), reason: id);
    }
    expect(body, isNot(contains('arcane-text-input-wrapper')));
  });
}
