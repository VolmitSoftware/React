library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../model/heatmap.dart';
import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../ui/reactor_ui.dart';

class HeatmapGridView extends StatelessWidget {
  final HeatmapGrid grid;

  const HeatmapGridView({required this.grid, super.key});

  static String _colorFor(double n) {
    final int r = (40 + n * (255 - 40)).round().clamp(0, 255);
    const int g = 90;
    final int b = (180 + n * (40 - 180)).round().clamp(0, 255);
    return 'rgb($r,$g,$b)';
  }

  static int _tickStride(int size) {
    if (size <= 9) return 1;
    return (size / 8).ceil();
  }

  static bool _showTick(int index, int size, int coordinate, int center) {
    return index == 0 ||
        index == size - 1 ||
        coordinate == center ||
        index % _tickStride(size) == 0;
  }

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final Widget header = dom.div(classes: 'reactor-heatmap-header', <Widget>[
      dom.div(classes: 'reactor-heatmap-heading', <Widget>[
        reactorEyebrow(grid.label),
        if (grid.world.isNotEmpty)
          dom.span(<Widget>[Component.text(grid.world)]),
      ]),
      dom.code(<Widget>[
        Component.text(
          '${grid.centerChunkX}, ${grid.centerChunkZ} · r${grid.radius}',
        ),
      ]),
    ]);

    final int cols = grid.size;
    final int rows = grid.size;
    final int originX = grid.centerChunkX - grid.radius;
    final int originZ = grid.centerChunkZ - grid.radius;
    final Map<(int, int), HeatmapCell> indexedCells = grid.indexCells();

    final List<Widget> cellWidgets = <Widget>[];
    for (int gz = 0; gz < rows; gz++) {
      for (int gx = 0; gx < cols; gx++) {
        final int chunkX = originX + gx;
        final int chunkZ = originZ + gz;
        final HeatmapCell? cell = indexedCells[(chunkX, chunkZ)];
        final bool center =
            chunkX == grid.centerChunkX && chunkZ == grid.centerChunkZ;
        final bool worldAxis = chunkX == 0 || chunkZ == 0;
        final String stateClasses = <String>[
          if (center) 'is-center',
          if (worldAxis) 'is-world-axis',
        ].join(' ');
        if (cell == null) {
          cellWidgets.add(
            dom.div(
              classes: 'reactor-heatmap-cell is-empty $stateClasses',
              styles: const dom.Styles(
                raw: <String, String>{'background': 'var(--muted)'},
              ),
              attributes: <String, String>{
                'data-cx': chunkX.toString(),
                'data-cz': chunkZ.toString(),
                'aria-hidden': 'true',
              },
              const <Widget>[],
            ),
          );
        } else {
          final double normalized = grid.max <= grid.min + 0.0001
              ? (cell.score > 0 ? 0.5 : 0.0)
              : ((cell.score - grid.min) / (grid.max - grid.min)).clamp(
                  0.0,
                  1.0,
                );
          final String color = _colorFor(normalized);
          cellWidgets.add(
            dom.div(
              classes: 'reactor-heatmap-cell $stateClasses',
              styles: dom.Styles(raw: <String, String>{'background': color}),
              attributes: <String, String>{
                'data-cx': chunkX.toString(),
                'data-cz': chunkZ.toString(),
                'data-score': cell.score.toString(),
                'tabindex': '0',
                'title': reactorText(
                  ReactorText.heatmapChunkTitle,
                  <String, Object?>{
                    'x': chunkX,
                    'z': chunkZ,
                    'score': cell.score.toStringAsFixed(2),
                  },
                ),
                'aria-label': reactorText(
                  ReactorText.heatmapChunkScore,
                  <String, Object?>{
                    'x': chunkX,
                    'z': chunkZ,
                    'score': cell.score.toStringAsFixed(2),
                  },
                ),
              },
              const <Widget>[],
            ),
          );
        }
      }
    }

    final List<Widget> xTicks = <Widget>[];
    for (int gx = 0; gx < cols; gx++) {
      final int chunkX = originX + gx;
      xTicks.add(
        dom.span(
          classes: chunkX == grid.centerChunkX ? 'is-center' : null,
          attributes: <String, String>{'data-axis-x': chunkX.toString()},
          <Widget>[
            if (_showTick(gx, cols, chunkX, grid.centerChunkX))
              Component.text(chunkX.toString()),
          ],
        ),
      );
    }

    final List<Widget> zTicks = <Widget>[];
    for (int gz = 0; gz < rows; gz++) {
      final int chunkZ = originZ + gz;
      zTicks.add(
        dom.span(
          classes: chunkZ == grid.centerChunkZ ? 'is-center' : null,
          attributes: <String, String>{'data-axis-z': chunkZ.toString()},
          <Widget>[
            if (_showTick(gz, rows, chunkZ, grid.centerChunkZ))
              Component.text(chunkZ.toString()),
          ],
        ),
      );
    }

    final Widget gridContainer = dom.div(
      classes: 'reactor-heatmap-grid',
      styles: dom.Styles(
        raw: <String, String>{'grid-template-columns': 'repeat($cols, 1fr)'},
      ),
      cellWidgets,
    );

    final Widget coordinatePlane = dom.div(
      classes: 'reactor-heatmap-plane',
      attributes: <String, String>{
        'dir': 'ltr',
        'data-origin-x': originX.toString(),
        'data-origin-z': originZ.toString(),
        'data-center-x': grid.centerChunkX.toString(),
        'data-center-z': grid.centerChunkZ.toString(),
      },
      <Widget>[
        dom.code(classes: 'reactor-heatmap-axis-corner', <Widget>[
          Component.text('Z \\ X'),
        ]),
        dom.div(
          classes: 'reactor-heatmap-axis reactor-heatmap-axis-x',
          styles: dom.Styles(
            raw: <String, String>{
              'grid-template-columns': 'repeat($cols, minmax(0, 1fr))',
            },
          ),
          xTicks,
        ),
        dom.div(
          classes: 'reactor-heatmap-axis reactor-heatmap-axis-z',
          styles: dom.Styles(
            raw: <String, String>{
              'grid-template-rows': 'repeat($rows, minmax(0, 1fr))',
            },
          ),
          zTicks,
        ),
        gridContainer,
      ],
    );

    final Widget legend = dom.div(classes: 'reactor-heatmap-legend', <Widget>[
      dom.span(<Widget>[
        Component.text(
          reactorText(ReactorText.commonLowValue, <String, Object?>{
            'value': grid.min.toStringAsFixed(2),
          }),
        ),
      ]),
      dom.span(<Widget>[
        Component.text(
          reactorText(ReactorText.commonHighValue, <String, Object?>{
            'value': grid.max.toStringAsFixed(2),
          }),
        ),
      ]),
    ]);

    return dom.section(classes: 'reactor-heatmap-view', <Widget>[
      header,
      if (grid.cells.isEmpty)
        dom.p(classes: 'reactor-heatmap-quiet', <Widget>[
          dom.strong(<Widget>[
            Component.text(reactorText(ReactorText.commonNoActivity)),
          ]),
          Component.text(
            ' · ${reactorText(ReactorText.heatmapNoScoredChunks)}',
          ),
        ]),
      dom.div(classes: 'reactor-heatmap-coordinate-key', <Widget>[
        dom.code(<Widget>[Component.text('N −Z')]),
        dom.code(<Widget>[Component.text('E +X')]),
        dom.code(<Widget>[Component.text('S +Z')]),
        dom.code(<Widget>[Component.text('W −X')]),
      ]),
      dom.div(classes: 'reactor-heatmap-canvas', <Widget>[coordinatePlane]),
      legend,
    ]);
  }
}
