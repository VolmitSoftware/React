library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr_test/server_test.dart';

import 'package:react_web/model/heatmap.dart';
import 'package:react_web/widget/heatmap_grid_view.dart';

const ShadcnStylesheet _sheet = ShadcnStylesheet(theme: ShadcnTheme.midnight);

Widget _wrap(Widget child) =>
    ArcaneThemeProvider(stylesheet: _sheet, child: child);

HeatmapGrid _grid({
  int originChunkX = -1,
  int originChunkZ = -1,
  int width = 3,
  int height = 3,
  int cellSizeChunks = 1,
  int centerChunkX = 0,
  int centerChunkZ = 0,
  HeatmapWorldBorder? worldBorder,
  List<HeatmapCell> cells = const <HeatmapCell>[],
}) => HeatmapGrid(
  id: 'test',
  label: 'Entity Pressure',
  world: 'minecraft:world_nether',
  centerChunkX: centerChunkX,
  centerChunkZ: centerChunkZ,
  radius: 64,
  originChunkX: originChunkX,
  originChunkZ: originChunkZ,
  width: width,
  height: height,
  cellSizeChunks: cellSizeChunks,
  capturedAtMs: 1750000000000,
  spawnChunkX: 0,
  spawnChunkZ: 0,
  worldBorder: worldBorder,
  min: 0,
  max: 10,
  cells: cells,
);

const HeatmapCell _sample = HeatmapCell(
  x: 0,
  z: 0,
  sizeChunks: 1,
  score: 7,
  averageScore: 4.5,
  samples: 12,
);

void main() {
  group('HeatmapGridView', () {
    testServer('renders a literal north-up coordinate plane from grid cells', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrap(
          HeatmapGridView(grid: _grid(cells: const <HeatmapCell>[_sample])),
        ),
      );
      final DocumentResponse response = await tester.request('/');

      expect(response.statusCode, equals(200));
      expect(response.body, contains('Entity Pressure'));
      expect(response.body, contains('minecraft:world_nether'));
      expect(response.body, contains('repeat(3,'));
      expect(response.body, contains('data-origin-x="-1"'));
      expect(response.body, contains('data-origin-z="-1"'));
      expect(response.body, contains('data-axis-x="-1"'));
      expect(response.body, contains('data-axis-z="1"'));
      expect(response.body, contains('N −Z'));
      expect(response.body, contains('E +X'));
    });

    testServer('renders peak, average, samples, and explicit sparse no-data', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrap(
          HeatmapGridView(grid: _grid(cells: const <HeatmapCell>[_sample])),
        ),
      );
      final DocumentResponse response = await tester.request('/');

      expect(response.body, contains('data-score="7'));
      expect(response.body, contains('data-average-score="4.5"'));
      expect(response.body, contains('data-samples="12"'));
      expect(response.body, contains('Peak 7.00'));
      expect(response.body, contains('Average 4.50'));
      expect(response.body, contains('12 samples'));
      expect('is-empty'.allMatches(response.body).length, equals(8));
      expect(response.body, contains('No data'));
    });

    testServer('uses one grid tab stop and a complete selected-cell ring', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrap(
          HeatmapGridView(grid: _grid(cells: const <HeatmapCell>[_sample])),
        ),
      );
      final DocumentResponse response = await tester.request('/');

      expect('tabindex="0"'.allMatches(response.body).length, equals(1));
      expect(response.body, contains('role="grid"'));
      expect(response.body, contains('aria-activedescendant='));
      expect(
        RegExp(
          r'class="[^"]*reactor-heatmap-cell[^"]*is-selected',
        ).hasMatch(response.body),
        isTrue,
      );
      expect(response.body, contains('aria-selected="true"'));
    });

    testServer('labels negative MCA regions on 32-chunk boundaries', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrap(
          HeatmapGridView(
            grid: _grid(
              originChunkX: -64,
              originChunkZ: -64,
              width: 5,
              height: 5,
              cellSizeChunks: 32,
            ),
          ),
        ),
      );
      final DocumentResponse response = await tester.request('/');

      expect(response.body, contains('data-region-x="-2"'));
      expect(response.body, contains('data-region-z="-2"'));
      expect(response.body, contains('is-region-west'));
      expect(response.body, contains('is-region-north'));
      expect(response.body, contains('MCA r.0.0'));
    });

    testServer('renders four actual world-border edges when fitted', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrap(
          HeatmapGridView(
            grid: _grid(
              originChunkX: -32,
              originChunkZ: -32,
              width: 65,
              height: 65,
              worldBorder: const HeatmapWorldBorder(
                centerBlockX: 0,
                centerBlockZ: 0,
                sizeBlocks: 512,
              ),
            ),
          ),
        ),
      );
      final DocumentResponse response = await tester.request('/');

      expect(response.body, contains('data-border-size="512.0"'));
      expect(
        'reactor-heatmap-world-border-edge'.allMatches(response.body).length,
        equals(4),
      );
      expect(response.body, contains('is-left'));
      expect(response.body, contains('is-right'));
      expect(response.body, contains('is-top'));
      expect(response.body, contains('is-bottom'));
    });

    testServer('does not invent viewport edges for an enclosing border', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrap(
          HeatmapGridView(
            grid: _grid(
              worldBorder: const HeatmapWorldBorder(
                centerBlockX: 0,
                centerBlockZ: 0,
                sizeBlocks: 4096,
              ),
            ),
          ),
        ),
      );
      final DocumentResponse response = await tester.request('/');

      expect(response.body, isNot(contains('reactor-heatmap-world-border')));
    });

    testServer('renders negative selected chunk and block ranges correctly', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrap(
          HeatmapGridView(
            grid: _grid(
              originChunkX: -8,
              originChunkZ: -16,
              width: 2,
              height: 2,
              cellSizeChunks: 8,
              centerChunkX: -7,
              centerChunkZ: -15,
              cells: const <HeatmapCell>[
                HeatmapCell(
                  x: -8,
                  z: -16,
                  sizeChunks: 8,
                  score: 5,
                  averageScore: 3,
                  samples: 9,
                ),
              ],
            ),
          ),
        ),
      );
      final DocumentResponse response = await tester.request('/');

      expect(response.body, contains('Chunks X -8…-1 · Z -16…-9'));
      expect(response.body, contains('Blocks X -128…-1 · Z -256…-129'));
    });
  });
}
