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

    if (grid.cells.isEmpty) {
      return dom.section(classes: 'reactor-heatmap-view', <Widget>[
        header,
        ReactorEmptyState(
          title: reactorText(ReactorText.commonNoActivity),
          description: reactorText(ReactorText.heatmapNoScoredChunks),
          icon: ArcaneIcon.grid3x3(size: IconSize.sm),
        ),
      ]);
    }

    final int cols = grid.size;
    final int rows = grid.size;
    final int originX = grid.centerChunkX - grid.radius;
    final int originZ = grid.centerChunkZ - grid.radius;

    final List<Widget> cellWidgets = <Widget>[];
    for (int gz = 0; gz < rows; gz++) {
      for (int gx = 0; gx < cols; gx++) {
        final int chunkX = originX + gx;
        final int chunkZ = originZ + gz;
        final HeatmapCell? cell = grid.cellAt(chunkX, chunkZ);
        if (cell == null) {
          cellWidgets.add(
            dom.div(
              classes: 'reactor-heatmap-cell is-empty',
              styles: const dom.Styles(
                raw: <String, String>{'background': 'var(--muted)'},
              ),
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
              classes: 'reactor-heatmap-cell',
              styles: dom.Styles(raw: <String, String>{'background': color}),
              attributes: <String, String>{
                'data-cx': chunkX.toString(),
                'data-cz': chunkZ.toString(),
                'data-score': cell.score.toString(),
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

    final Widget gridContainer = dom.div(
      classes: 'reactor-heatmap-grid',
      styles: dom.Styles(
        raw: <String, String>{'grid-template-columns': 'repeat($cols, 1fr)'},
      ),
      cellWidgets,
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
      dom.div(classes: 'reactor-heatmap-canvas', <Widget>[gridContainer]),
      legend,
    ]);
  }
}
