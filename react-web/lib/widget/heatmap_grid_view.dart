/// The heatmap plane: a north-up grid of chunk cells with its X and Z axes,
/// the world border and a colour legend. It is a square that scales to the
/// width it is given and never scrolls; score labels show only while the
/// cells are wide enough for them (web/styles/heatmap.css). The picked
/// cell's facts are [HeatmapCellDetails], drawn by the page.
library;

import 'dart:math' as math;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component, EventCallback;

import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/heatmap.dart';
import '../model/units.dart';
import '../ui/kit/info_tip.dart';
import 'heatmap_interaction.dart';

/// Grids up to this many cells across carry score labels (when wide enough).
const int heatmapLabelledCells = 17;

class HeatmapGridView extends StatefulWidget {
  final HeatmapGrid grid;

  /// The unit of the scores ("ms", "%"), for the legend.
  final String? unit;
  final HeatmapTarget? selection;
  final void Function(HeatmapTarget target)? onSelectionChanged;
  final void Function(int horizontal, int vertical)? onPan;
  final void Function(bool zoomIn)? onZoom;

  /// Enter on the grid: the keyboard's way to act on the picked cell.
  final void Function()? onPick;

  const HeatmapGridView({
    required this.grid,
    this.unit,
    this.selection,
    this.onSelectionChanged,
    this.onPan,
    this.onZoom,
    this.onPick,
    super.key,
  });

  @override
  State<HeatmapGridView> createState() => _HeatmapGridViewState();
}

class _HeatmapGridViewState extends State<HeatmapGridView> {
  int? _selectedChunkX;
  int? _selectedChunkZ;
  void Function()? _uninstallInteraction;
  bool _installPending = false;
  bool _disposed = false;
  HeatmapGrid? _cachedGrid;
  ReactorLocaleScope? _cachedLocale;
  Map<(int, int), HeatmapCell> _indexedCells = <(int, int), HeatmapCell>{};
  final Map<(int, int), (bool, Widget)> _cells = <(int, int), (bool, Widget)>{};

  String get _gridId => 'reactor-heatmap-grid-${component.grid.id}';

  @override
  void dispose() {
    _disposed = true;
    _uninstallInteraction?.call();
    _uninstallInteraction = null;
    super.dispose();
  }

  void _scheduleInteraction() {
    if (_installPending || _disposed || _uninstallInteraction != null) return;
    _installPending = true;
    context.binding.addPostFrameCallback(() {
      _installPending = false;
      if (_disposed || _uninstallInteraction != null) return;
      _uninstallInteraction = installHeatmapInteraction(
        elementId: _gridId,
        onZoom: (bool zoomIn) => component.onZoom?.call(zoomIn),
        onPan: (int horizontal, int vertical) =>
            component.onPan?.call(horizontal, vertical),
        onKey: _onKey,
      );
    });
  }

  void _ensureSelection() {
    final HeatmapGrid grid = component.grid;
    final HeatmapTarget? controlled = component.selection;
    final int selectedX =
        controlled?.originChunkX ?? _selectedChunkX ?? grid.centerChunkX;
    final int selectedZ =
        controlled?.originChunkZ ?? _selectedChunkZ ?? grid.centerChunkZ;
    if (selectedX >= grid.originChunkX &&
        selectedX <= grid.maximumChunkX &&
        selectedZ >= grid.originChunkZ &&
        selectedZ <= grid.maximumChunkZ) {
      _selectedChunkX = _alignedOriginX(selectedX);
      _selectedChunkZ = _alignedOriginZ(selectedZ);
      return;
    }
    _selectedChunkX = _alignedOriginX(grid.centerChunkX);
    _selectedChunkZ = _alignedOriginZ(grid.centerChunkZ);
  }

  int _alignedOriginX(int chunkX) {
    final HeatmapGrid grid = component.grid;
    final int column = ((chunkX - grid.originChunkX) / grid.cellSizeChunks)
        .floor()
        .clamp(0, grid.columns - 1);
    return grid.originChunkX + column * grid.cellSizeChunks;
  }

  int _alignedOriginZ(int chunkZ) {
    final HeatmapGrid grid = component.grid;
    final int row = ((chunkZ - grid.originChunkZ) / grid.cellSizeChunks)
        .floor()
        .clamp(0, grid.rows - 1);
    return grid.originChunkZ + row * grid.cellSizeChunks;
  }

  void _select(int chunkX, int chunkZ) {
    final int selectedX = _alignedOriginX(chunkX);
    final int selectedZ = _alignedOriginZ(chunkZ);
    final HeatmapTarget target = HeatmapTarget(
      world: component.grid.world,
      originChunkX: selectedX,
      originChunkZ: selectedZ,
      sizeChunks: component.grid.cellSizeChunks,
    );
    final void Function(HeatmapTarget target)? callback =
        component.onSelectionChanged;
    if (callback != null) {
      callback(target);
      return;
    }
    setState(() {
      _selectedChunkX = selectedX;
      _selectedChunkZ = selectedZ;
    });
  }

  void _onKey(String key) {
    if (key == 'Enter') {
      component.onPick?.call();
      return;
    }
    _ensureSelection();
    final HeatmapGrid grid = component.grid;
    final int selectedX = _selectedChunkX!;
    final int selectedZ = _selectedChunkZ!;
    final int nextX = switch (key) {
      'ArrowLeft' => math.max(
        grid.originChunkX,
        selectedX - grid.cellSizeChunks,
      ),
      'ArrowRight' => math.min(
        grid.originChunkX + (grid.columns - 1) * grid.cellSizeChunks,
        selectedX + grid.cellSizeChunks,
      ),
      'Home' => _alignedOriginX(grid.centerChunkX),
      _ => selectedX,
    };
    final int nextZ = switch (key) {
      'ArrowUp' => math.max(grid.originChunkZ, selectedZ - grid.cellSizeChunks),
      'ArrowDown' => math.min(
        grid.originChunkZ + (grid.rows - 1) * grid.cellSizeChunks,
        selectedZ + grid.cellSizeChunks,
      ),
      'Home' => _alignedOriginZ(grid.centerChunkZ),
      _ => selectedZ,
    };
    if (nextX == selectedX && nextZ == selectedZ) return;
    _select(nextX, nextZ);
  }

  /// One sequential scale in the theme's tokens: nothing happening is the
  /// quiet muted surface, the top of the scale the destructive red.
  static String _colorFor(double normalized) =>
      'color-mix(in oklab, var(--destructive) ${(normalized * 100).round()}%, '
      'var(--muted))';

  /// Ticks every [_tickStride] cells counted from the centre cell, so they
  /// are evenly spaced (-8, -4, 0, 4, 8), about five a side.
  static int _tickStride(int size) => size <= 9 ? 1 : (size / 5).ceil();

  static bool _showTick(int index, int size, int centerIndex) =>
      (index - centerIndex) % _tickStride(size) == 0;

  @override
  Widget build(BuildContext context) {
    final ReactorLocaleScope? locale = dependOnReactorLocale(context);
    if (!identical(_cachedGrid, component.grid) ||
        !identical(_cachedLocale, locale)) {
      _cachedGrid = component.grid;
      _cachedLocale = locale;
      _indexedCells = component.grid.indexCells();
      _cells.clear();
    }
    _ensureSelection();
    _scheduleInteraction();
    final HeatmapGrid grid = component.grid;
    final int columns = grid.columns;
    final int rows = grid.rows;
    final int across = math.max(columns, rows);
    final int selectedX = _selectedChunkX!;
    final int selectedZ = _selectedChunkZ!;
    final bool labelled = across <= heatmapLabelledCells;
    final bool showRegionBoundaries = grid.cellSizeChunks <= 32 && across <= 96;

    final List<Widget> cells = <Widget>[];
    for (int row = 0; row < rows; row++) {
      for (int column = 0; column < columns; column++) {
        final int chunkX = grid.originChunkX + column * grid.cellSizeChunks;
        final int chunkZ = grid.originChunkZ + row * grid.cellSizeChunks;
        final bool selected = chunkX == selectedX && chunkZ == selectedZ;
        final (bool, Widget)? cached = _cells[(chunkX, chunkZ)];
        if (cached != null && cached.$1 == selected) {
          cells.add(cached.$2);
          continue;
        }
        cells.add(
          _cell(
            grid,
            chunkX,
            chunkZ,
            selected: selected,
            labelled: labelled,
            regions: showRegionBoundaries,
          ),
        );
        _cells[(chunkX, chunkZ)] = (selected, cells.last);
      }
    }

    final Widget gridWidget = dom.div(
      id: _gridId,
      classes: 'reactor-heatmap-grid',
      styles: dom.Styles(
        raw: <String, String>{
          'grid-template-columns': 'repeat($columns, minmax(0, 1fr))',
          'grid-template-rows': 'repeat($rows, minmax(0, 1fr))',
        },
      ),
      attributes: <String, String>{
        'role': 'grid',
        'tabindex': '0',
        'aria-rowcount': rows.toString(),
        'aria-colcount': columns.toString(),
        'aria-activedescendant': '$_gridId-cell-$selectedX-$selectedZ',
        'aria-label': reactorText(
          ReactorText.heatmapNorthUpGrid,
          <String, Object?>{'label': grid.label},
        ),
      },
      cells,
    );

    final Widget plane = dom.div(
      classes: 'reactor-heatmap-plane',
      attributes: <String, String>{
        'dir': 'ltr',
        if (labelled) 'data-labels': across <= 9 ? 's' : 'm',
        'data-origin-x': grid.originChunkX.toString(),
        'data-origin-z': grid.originChunkZ.toString(),
        'data-center-x': grid.centerChunkX.toString(),
        'data-center-z': grid.centerChunkZ.toString(),
        'data-cell-size': grid.cellSizeChunks.toString(),
      },
      <Widget>[
        dom.code(
          classes: 'reactor-heatmap-corner',
          attributes: const <String, String>{'aria-hidden': 'true'},
          <Widget>[Component.text('Z \\ X')],
        ),
        _axis(grid, horizontal: true),
        _axis(grid, horizontal: false),
        dom.div(
          classes: 'reactor-heatmap-stage',
          styles: dom.Styles(
            raw: <String, String>{'aspect-ratio': '$columns / $rows'},
          ),
          <Widget>[gridWidget, ?_worldBorderOverlay(grid)],
        ),
      ],
    );

    return dom.div(classes: 'ui-stack', <Widget>[plane, _legend(grid)]);
  }

  Widget _cell(
    HeatmapGrid grid,
    int chunkX,
    int chunkZ, {
    required bool selected,
    required bool labelled,
    required bool regions,
  }) {
    final HeatmapCell? cell = _indexedCells[(chunkX, chunkZ)];
    final int maximumChunkX = chunkX + grid.cellSizeChunks - 1;
    final int maximumChunkZ = chunkZ + grid.cellSizeChunks - 1;
    bool covers(int x, int z) =>
        x >= chunkX && x <= maximumChunkX && z >= chunkZ && z <= maximumChunkZ;
    final bool center = covers(grid.centerChunkX, grid.centerChunkZ);
    final bool spawn = covers(grid.spawnChunkX, grid.spawnChunkZ);
    final bool worldAxis =
        (chunkX <= 0 && maximumChunkX >= 0) ||
        (chunkZ <= 0 && maximumChunkZ >= 0);
    final bool regionWest = regions && _floorMod(chunkX, 32) == 0;
    final bool regionNorth = regions && _floorMod(chunkZ, 32) == 0;
    final String classes = <String>[
      'reactor-heatmap-cell',
      if (cell == null) 'is-empty',
      if (selected) 'is-selected',
      if (center) 'is-center',
      if (spawn) 'is-spawn',
      if (worldAxis) 'is-world-axis',
      if (regionWest) 'is-region-west',
      if (regionNorth) 'is-region-north',
    ].join(' ');
    final double normalized = cell == null
        ? 0
        : grid.max <= grid.min + 0.0001
        ? (cell.score > 0 ? 0.5 : 0.0)
        : ((cell.score - grid.min) / (grid.max - grid.min)).clamp(0.0, 1.0);
    final String coordinateLabel = heatmapChunkText(
      chunkX,
      chunkZ,
      grid.cellSizeChunks,
    );
    final String scoreLabel = cell == null
        ? reactorText(ReactorText.heatmapNoLoadedSamples)
        : heatmapStatisticsText(cell);
    return dom.div(
      id: '$_gridId-cell-$chunkX-$chunkZ',
      classes: classes,
      styles: cell == null
          ? null
          : dom.Styles(
              raw: <String, String>{
                '--heatmap-cell-color': _colorFor(normalized),
                // The score label stays readable on both ends of the scale.
                'color': normalized < 0.5
                    ? 'var(--foreground)'
                    : 'var(--destructive-foreground, #fff)',
              },
            ),
      attributes: <String, String>{
        'role': 'gridcell',
        'aria-selected': selected.toString(),
        'aria-label': '$coordinateLabel. $scoreLabel.',
        'data-cx': chunkX.toString(),
        'data-cz': chunkZ.toString(),
        'data-cell-size': grid.cellSizeChunks.toString(),
        'data-region-x': _floorDiv(chunkX, 32).toString(),
        'data-region-z': _floorDiv(chunkZ, 32).toString(),
        if (cell != null) 'data-score': cell.score.toString(),
        if (cell != null) 'data-average-score': cell.averageScore.toString(),
        if (cell != null) 'data-samples': cell.samples.toString(),
      },
      events: <String, EventCallback>{
        'click': (event) => _select(chunkX, chunkZ),
      },
      <Widget>[
        if (labelled && cell != null)
          dom.span(
            classes: 'reactor-heatmap-cell-label',
            attributes: const <String, String>{'aria-hidden': 'true'},
            <Widget>[Component.text(cell.score.round().toString())],
          ),
      ],
    );
  }

  Widget _axis(HeatmapGrid grid, {required bool horizontal}) {
    final int size = horizontal ? grid.columns : grid.rows;
    final int origin = horizontal ? grid.originChunkX : grid.originChunkZ;
    final int centerChunk = horizontal ? grid.centerChunkX : grid.centerChunkZ;
    return dom.div(
      classes: horizontal
          ? 'reactor-heatmap-axis is-x'
          : 'reactor-heatmap-axis is-z',
      styles: dom.Styles(
        raw: <String, String>{
          horizontal ? 'grid-template-columns' : 'grid-template-rows':
              'repeat($size, minmax(0, 1fr))',
        },
      ),
      attributes: const <String, String>{'aria-hidden': 'true'},
      <Widget>[
        for (int index = 0; index < size; index++)
          _tick(
            origin + index * grid.cellSizeChunks,
            grid.cellSizeChunks,
            centerChunk,
            index,
            size,
            horizontal: horizontal,
          ),
      ],
    );
  }

  Widget _tick(
    int chunk,
    int cellSize,
    int centerChunk,
    int index,
    int size, {
    required bool horizontal,
  }) {
    final bool center = centerChunk >= chunk && centerChunk < chunk + cellSize;
    final int centerIndex = index + (centerChunk - chunk) ~/ cellSize;
    return dom.span(
      classes: center ? 'is-center' : null,
      attributes: <String, String>{
        horizontal ? 'data-axis-x' : 'data-axis-z': chunk.toString(),
      },
      <Widget>[
        if (_showTick(index, size, centerIndex))
          Component.text(chunk.toString()),
      ],
    );
  }

  Widget _legend(HeatmapGrid grid) {
    final String compass = <String>[
      '${reactorText(ReactorText.heatmapsNorth)} −Z',
      '${reactorText(ReactorText.heatmapsEast)} +X',
      '${reactorText(ReactorText.heatmapsSouth)} +Z',
      '${reactorText(ReactorText.heatmapsWest)} −X',
    ].join(' · ');
    return dom.div(classes: 'reactor-heatmap-legend ui-caption', <Widget>[
      dom.span(classes: 'ui-tip-label', <Widget>[
        dom.span(<Widget>[
          Component.text(
            reactorText(ReactorText.commonLowValue, <String, Object?>{
              'value': withUnit(compactNumber(grid.min), component.unit),
            }),
          ),
        ]),
        const dom.span(
          classes: 'reactor-heatmap-scale-bar',
          attributes: <String, String>{'aria-hidden': 'true'},
          <Widget>[],
        ),
        dom.span(<Widget>[
          Component.text(
            reactorText(ReactorText.commonHighValue, <String, Object?>{
              // Cells past the top of the scale draw at full red; "10+"
              // says the scale stops there.
              'value': withUnit(
                '${compactNumber(grid.max)}'
                '${grid.cells.any((HeatmapCell cell) => cell.score > grid.max) ? '+' : ''}',
                component.unit,
              ),
            }),
          ),
        ]),
      ]),
      // What a hatched cell means.
      dom.span(classes: 'ui-tip-label', <Widget>[
        const dom.span(
          classes: 'reactor-heatmap-swatch',
          attributes: <String, String>{'aria-hidden': 'true'},
          <Widget>[],
        ),
        dom.span(<Widget>[
          Component.text(reactorText(ReactorText.heatmapNoLoadedSamples)),
        ]),
      ]),
      dom.span(classes: 'ui-tip-label', <Widget>[
        dom.span(<Widget>[Component.text(compass)]),
        InfoTip(tip: reactorText(ReactorText.heatmapInteractionHint)),
      ]),
    ]);
  }

  Widget? _worldBorderOverlay(HeatmapGrid grid) {
    final HeatmapWorldBorder? border = grid.worldBorder;
    if (border == null || border.sizeBlocks <= 0) return null;
    final double spanChunksX = grid.width * grid.cellSizeChunks.toDouble();
    final double spanChunksZ = grid.height * grid.cellSizeChunks.toDouble();
    final double left =
        ((border.minimumBlockX / 16 - grid.originChunkX) / spanChunksX) * 100;
    final double right =
        ((border.maximumBlockX / 16 - grid.originChunkX) / spanChunksX) * 100;
    final double top =
        ((border.minimumBlockZ / 16 - grid.originChunkZ) / spanChunksZ) * 100;
    final double bottom =
        ((border.maximumBlockZ / 16 - grid.originChunkZ) / spanChunksZ) * 100;
    if (right <= 0 || left >= 100 || bottom <= 0 || top >= 100) return null;
    final bool leftVisible = left >= 0 && left <= 100;
    final bool rightVisible = right >= 0 && right <= 100;
    final bool topVisible = top >= 0 && top <= 100;
    final bool bottomVisible = bottom >= 0 && bottom <= 100;
    if (!leftVisible && !rightVisible && !topVisible && !bottomVisible) {
      return null;
    }
    final double clippedLeft = left.clamp(0, 100);
    final double clippedRight = right.clamp(0, 100);
    final double clippedTop = top.clamp(0, 100);
    final double clippedBottom = bottom.clamp(0, 100);
    return dom.div(
      classes: 'reactor-heatmap-world-border',
      attributes: <String, String>{
        'aria-label': reactorText(
          ReactorText.heatmapWorldBorderLabel,
          <String, Object?>{
            'x': border.centerBlockX,
            'z': border.centerBlockZ,
            'size': border.sizeBlocks,
          },
        ),
        'data-border-center-x': border.centerBlockX.toString(),
        'data-border-center-z': border.centerBlockZ.toString(),
        'data-border-size': border.sizeBlocks.toString(),
      },
      <Widget>[
        if (leftVisible)
          _borderEdge('is-left', <String, String>{
            'left': '$left%',
            'top': '$clippedTop%',
            'height': '${clippedBottom - clippedTop}%',
          }),
        if (rightVisible)
          _borderEdge('is-right', <String, String>{
            'left': '$right%',
            'top': '$clippedTop%',
            'height': '${clippedBottom - clippedTop}%',
          }),
        if (topVisible)
          _borderEdge('is-top', <String, String>{
            'left': '$clippedLeft%',
            'top': '$top%',
            'width': '${clippedRight - clippedLeft}%',
          }),
        if (bottomVisible)
          _borderEdge('is-bottom', <String, String>{
            'left': '$clippedLeft%',
            'top': '$bottom%',
            'width': '${clippedRight - clippedLeft}%',
          }),
      ],
    );
  }

  Widget _borderEdge(String edgeClass, Map<String, String> styles) => dom.span(
    classes: 'reactor-heatmap-world-border-edge $edgeClass',
    styles: dom.Styles(raw: styles),
    attributes: const <String, String>{'aria-hidden': 'true'},
    const <Widget>[],
  );
}

int _floorDiv(int value, int divisor) => (value / divisor).floor();

int _floorMod(int value, int divisor) =>
    value - _floorDiv(value, divisor) * divisor;

/// "Chunk 3, -2", or the chunk range of a cell that spans several.
String heatmapChunkText(int chunkX, int chunkZ, int size) => size == 1
    ? reactorText(ReactorText.heatmapChunkCoordinate, <String, Object?>{
        'x': chunkX,
        'z': chunkZ,
      })
    : reactorText(ReactorText.heatmapChunkRange, <String, Object?>{
        'minX': chunkX,
        'maxX': chunkX + size - 1,
        'minZ': chunkZ,
        'maxZ': chunkZ + size - 1,
      });

/// "Peak 7.00 · Average 4.50 · 12 samples".
String heatmapStatisticsText(HeatmapCell cell) =>
    reactorText(ReactorText.heatmapStatistics, <String, Object?>{
      'peak': cell.score.toStringAsFixed(2),
      'average': cell.averageScore.toStringAsFixed(2),
      'samples': cell.samples,
    });

/// The picked cell in words: its chunks, blocks and region file, and its
/// samples. Plain text, for under the plane in the same card.
class HeatmapCellDetails extends StatelessWidget {
  const HeatmapCellDetails({
    required this.grid,
    required this.target,
    super.key,
  });

  final HeatmapGrid grid;
  final HeatmapTarget target;

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final int chunkX = target.originChunkX;
    final int chunkZ = target.originChunkZ;
    final HeatmapCell? cell = grid.cellAtOrigin(chunkX, chunkZ);
    final int size = cell?.sizeChunks ?? target.sizeChunks;
    final String blocks = reactorText(
      ReactorText.heatmapBlockRange,
      <String, Object?>{
        'minX': chunkX * 16,
        'maxX': (chunkX + size) * 16 - 1,
        'minZ': chunkZ * 16,
        'maxZ': (chunkZ + size) * 16 - 1,
      },
    );
    final String region =
        'MCA r.${_floorDiv(chunkX, 32)}.${_floorDiv(chunkZ, 32)}';
    return dom.div(
      classes: 'reactor-ltr',
      attributes: const <String, String>{'aria-live': 'polite'},
      <Widget>[
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          spacing: 4,
          children: <Widget>[
            dom.strong(<Widget>[
              Component.text(heatmapChunkText(chunkX, chunkZ, size)),
            ]),
            dom.span(classes: 'ui-caption', <Widget>[
              Component.text('$blocks · $region'),
            ]),
            dom.span(classes: 'ui-caption', <Widget>[
              Component.text(
                cell == null
                    ? reactorText(ReactorText.heatmapNoLoadedSamples)
                    : heatmapStatisticsText(cell),
              ),
            ]),
          ],
        ),
      ],
    );
  }
}
