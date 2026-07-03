library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../model/heatmap.dart';
import '../widget/section_card.dart';

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
    final Widget header = dom.div(
      styles: kSectionHeadingStyles,
      <Widget>[
        Component.text(
          grid.world.isNotEmpty ? '${grid.label} — ${grid.world}' : grid.label,
        ),
      ],
    );

    if (grid.cells.isEmpty) {
      return dom.div(
        <Widget>[
          header,
          dom.div(
            styles: const dom.Styles(raw: <String, String>{
              'color': 'var(--muted-foreground)',
              'font-size': '0.875rem',
            }),
            <Widget>[Component.text('No activity')],
          ),
        ],
      );
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
          cellWidgets.add(dom.div(
            styles: const dom.Styles(raw: <String, String>{
              'background': 'var(--muted)',
            }),
            const <Widget>[],
          ));
        } else {
          final double normalized = grid.max <= grid.min + 0.0001
              ? (cell.score > 0 ? 0.5 : 0.0)
              : ((cell.score - grid.min) / (grid.max - grid.min)).clamp(0.0, 1.0);
          final String color = _colorFor(normalized);
          cellWidgets.add(dom.div(
            styles: dom.Styles(raw: <String, String>{
              'background': color,
            }),
            attributes: <String, String>{
              'data-cx': chunkX.toString(),
              'data-cz': chunkZ.toString(),
              'data-score': cell.score.toString(),
            },
            const <Widget>[],
          ));
        }
      }
    }

    final Widget gridContainer = dom.div(
      styles: dom.Styles(raw: <String, String>{
        'display': 'grid',
        'grid-template-columns': 'repeat($cols, 1fr)',
        'gap': '1px',
        'aspect-ratio': '1/1',
        'width': '100%',
        'max-width': '320px',
      }),
      cellWidgets,
    );

    final Widget legend = dom.div(
      styles: const dom.Styles(raw: <String, String>{
        'display': 'flex',
        'justify-content': 'space-between',
        'font-size': '0.75rem',
        'color': 'var(--muted-foreground)',
      }),
      <Widget>[
        Component.text(grid.min.toStringAsFixed(2)),
        Component.text(grid.max.toStringAsFixed(2)),
      ],
    );

    return dom.div(
      <Widget>[
        header,
        gridContainer,
        legend,
      ],
    );
  }
}
