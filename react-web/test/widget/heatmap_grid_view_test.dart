library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr_test/server_test.dart';

import 'package:react_web/model/heatmap.dart';
import 'package:react_web/widget/heatmap_grid_view.dart';

const ShadcnStylesheet _sheet = ShadcnStylesheet(theme: ShadcnTheme.midnight);

Widget _wrap(Widget child) =>
    ArcaneThemeProvider(stylesheet: _sheet, child: child);

void main() {
  group('HeatmapGridView', () {
    testServer('renders the heatmap label and world', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrap(
          const HeatmapGridView(
            grid: HeatmapGrid(
              id: 'test',
              label: 'Entity Pressure',
              world: 'world',
              centerChunkX: 0,
              centerChunkZ: 0,
              radius: 1,
              min: 0.0,
              max: 10.0,
              cells: <HeatmapCell>[],
            ),
          ),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Entity Pressure'),
        isTrue,
        reason: 'label must appear in rendered HTML',
      );
      expect(
        res.body.contains('world'),
        isTrue,
        reason: 'world must appear in rendered HTML',
      );
    });

    testServer('renders a CSS grid sized to radius', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrap(
          const HeatmapGridView(
            grid: HeatmapGrid(
              id: 'test',
              label: 'Test',
              world: '',
              centerChunkX: 0,
              centerChunkZ: 0,
              radius: 1,
              min: 0.0,
              max: 10.0,
              cells: <HeatmapCell>[HeatmapCell(x: 0, z: 0, score: 5.0)],
            ),
          ),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('repeat(3,'),
        isTrue,
        reason: 'radius=1 produces a 3x3 grid and must use repeat(3,',
      );
    });

    testServer('renders scored cells with data-score attributes', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrap(
          const HeatmapGridView(
            grid: HeatmapGrid(
              id: 'test',
              label: 'Test',
              world: '',
              centerChunkX: 0,
              centerChunkZ: 0,
              radius: 1,
              min: 3.0,
              max: 7.0,
              cells: <HeatmapCell>[
                HeatmapCell(x: 0, z: 0, score: 3.0),
                HeatmapCell(x: 1, z: 0, score: 7.0),
              ],
            ),
          ),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('data-score="3'),
        isTrue,
        reason: 'cell with score 3.0 must carry data-score attribute',
      );
      expect(
        res.body.contains('data-score="7'),
        isTrue,
        reason: 'cell with score 7.0 must carry data-score attribute',
      );
      expect(
        res.body.contains('data-cx="0"'),
        isTrue,
        reason: 'scored cell must carry data-cx attribute',
      );
      expect(
        res.body.contains('data-cz="0"'),
        isTrue,
        reason: 'scored cell must carry data-cz attribute',
      );
    });

    testServer('renders min and max legend values', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrap(
          const HeatmapGridView(
            grid: HeatmapGrid(
              id: 'test',
              label: 'Test',
              world: '',
              centerChunkX: 0,
              centerChunkZ: 0,
              radius: 1,
              min: 3.0,
              max: 7.0,
              cells: <HeatmapCell>[HeatmapCell(x: 0, z: 0, score: 3.0)],
            ),
          ),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('3.00') || res.body.contains('>3<'),
        isTrue,
        reason: 'min value 3 must appear as text in the legend',
      );
      expect(
        res.body.contains('7.00') || res.body.contains('>7<'),
        isTrue,
        reason: 'max value 7 must appear as text in the legend',
      );
    });

    testServer('empty cells renders No activity and no grid', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrap(
          const HeatmapGridView(
            grid: HeatmapGrid(
              id: 'test',
              label: 'Test',
              world: '',
              centerChunkX: 0,
              centerChunkZ: 0,
              radius: 1,
              min: 0.0,
              max: 0.0,
              cells: <HeatmapCell>[],
            ),
          ),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('No activity'),
        isTrue,
        reason: 'empty grid must render No activity text',
      );
      expect(
        res.body.contains('repeat('),
        isFalse,
        reason: 'empty grid must not render a CSS grid repeat container',
      );
    });

    testServer('min==max degenerate scale does not divide by zero', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrap(
          const HeatmapGridView(
            grid: HeatmapGrid(
              id: 'test',
              label: 'Test',
              world: '',
              centerChunkX: 0,
              centerChunkZ: 0,
              radius: 1,
              min: 5.0,
              max: 5.0,
              cells: <HeatmapCell>[HeatmapCell(x: 0, z: 0, score: 5.0)],
            ),
          ),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('data-score="5'),
        isTrue,
        reason: 'degenerate min==max must still render the scored cell',
      );
    });
  });
}
