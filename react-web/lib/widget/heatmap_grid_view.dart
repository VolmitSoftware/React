library;

import 'dart:math' as math;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component, EventCallback;

import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/heatmap.dart';
import '../ui/reactor_ui.dart';
import 'heatmap_interaction.dart';

class HeatmapGridView extends StatefulWidget {
  final HeatmapGrid grid;
  final HeatmapTarget? selection;
  final void Function(HeatmapTarget target)? onSelectionChanged;
  final void Function(int horizontal, int vertical)? onPan;
  final void Function(bool zoomIn)? onZoom;

  const HeatmapGridView({
    required this.grid,
    this.selection,
    this.onSelectionChanged,
    this.onPan,
    this.onZoom,
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

  static String _colorFor(double normalized) {
    final int red = (38 + normalized * 217).round().clamp(0, 255);
    final int green = (104 - normalized * 38).round().clamp(0, 255);
    final int blue = (176 - normalized * 128).round().clamp(0, 255);
    return 'rgb($red,$green,$blue)';
  }

  static int _tickStride(int size) {
    if (size <= 9) return 1;
    return (size / 8).ceil();
  }

  static bool _showTick(int index, int size, bool center) =>
      index == 0 ||
      index == size - 1 ||
      center ||
      index % _tickStride(size) == 0;

  static int _floorDiv(int value, int divisor) => (value / divisor).floor();

  static int _floorMod(int value, int divisor) =>
      value - _floorDiv(value, divisor) * divisor;

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    _ensureSelection();
    _scheduleInteraction();
    final HeatmapGrid grid = component.grid;
    final int columns = grid.columns;
    final int rows = grid.rows;
    final int selectedX = _selectedChunkX!;
    final int selectedZ = _selectedChunkZ!;
    final Map<(int, int), HeatmapCell> indexedCells = grid.indexCells();
    final HeatmapCell? selectedCell = indexedCells[(selectedX, selectedZ)];
    final bool showCellDetails = math.max(columns, rows) <= 24;
    final bool showEmptyLabel = math.max(columns, rows) <= 16;
    final bool showRegionBoundaries =
        grid.cellSizeChunks <= 32 && math.max(columns, rows) <= 96;
    final bool showRegionLabels =
        showRegionBoundaries && math.max(columns, rows) <= 16;

    final Widget header = dom.div(classes: 'reactor-heatmap-header', <Widget>[
      dom.div(classes: 'reactor-heatmap-heading', <Widget>[
        reactorEyebrow(grid.label),
        if (grid.world.isNotEmpty)
          dom.span(<Widget>[Component.text(grid.world)]),
      ]),
      dom.code(<Widget>[
        Component.text(
          '${grid.centerChunkX}, ${grid.centerChunkZ} · r${grid.radius} · ${reactorText(ReactorText.heatmapCellScale, <String, Object?>{'size': grid.cellSizeChunks})}',
        ),
      ]),
    ]);

    final List<Widget> cells = <Widget>[];
    for (int row = 0; row < rows; row++) {
      for (int column = 0; column < columns; column++) {
        final int chunkX = grid.originChunkX + column * grid.cellSizeChunks;
        final int chunkZ = grid.originChunkZ + row * grid.cellSizeChunks;
        final HeatmapCell? cell = indexedCells[(chunkX, chunkZ)];
        final int maximumChunkX = chunkX + grid.cellSizeChunks - 1;
        final int maximumChunkZ = chunkZ + grid.cellSizeChunks - 1;
        final bool selected = chunkX == selectedX && chunkZ == selectedZ;
        final bool center =
            grid.centerChunkX >= chunkX &&
            grid.centerChunkX <= maximumChunkX &&
            grid.centerChunkZ >= chunkZ &&
            grid.centerChunkZ <= maximumChunkZ;
        final bool spawn =
            grid.spawnChunkX >= chunkX &&
            grid.spawnChunkX <= maximumChunkX &&
            grid.spawnChunkZ >= chunkZ &&
            grid.spawnChunkZ <= maximumChunkZ;
        final bool worldAxis =
            (chunkX <= 0 && maximumChunkX >= 0) ||
            (chunkZ <= 0 && maximumChunkZ >= 0);
        final bool regionWest =
            showRegionBoundaries && _floorMod(chunkX, 32) == 0;
        final bool regionNorth =
            showRegionBoundaries && _floorMod(chunkZ, 32) == 0;
        final bool regionLabel = showRegionLabels && regionWest && regionNorth;
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
        final String coordinateLabel = grid.cellSizeChunks == 1
            ? reactorText(ReactorText.heatmapChunkCoordinate, <String, Object?>{
                'x': chunkX,
                'z': chunkZ,
              })
            : reactorText(ReactorText.heatmapChunkRange, <String, Object?>{
                'minX': chunkX,
                'maxX': maximumChunkX,
                'minZ': chunkZ,
                'maxZ': maximumChunkZ,
              });
        final String scoreLabel = cell == null
            ? reactorText(ReactorText.heatmapNoLoadedSamples)
            : reactorText(ReactorText.heatmapStatistics, <String, Object?>{
                'peak': cell.score.toStringAsFixed(2),
                'average': cell.averageScore.toStringAsFixed(2),
                'samples': cell.samples,
              });
        cells.add(
          dom.div(
            id: '$_gridId-cell-$chunkX-$chunkZ',
            classes: classes,
            styles: cell == null
                ? null
                : dom.Styles(
                    raw: <String, String>{
                      '--heatmap-cell-color': _colorFor(normalized),
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
              if (cell != null)
                'data-average-score': cell.averageScore.toString(),
              if (cell != null) 'data-samples': cell.samples.toString(),
            },
            events: <String, EventCallback>{
              'click': (event) => _select(chunkX, chunkZ),
            },
            <Widget>[
              if (regionLabel)
                dom.span(classes: 'reactor-heatmap-region-label', <Widget>[
                  Component.text(
                    'r.${_floorDiv(chunkX, 32)}.${_floorDiv(chunkZ, 32)}',
                  ),
                ]),
              if (showCellDetails)
                dom.span(classes: 'reactor-heatmap-cell-coordinate', <Widget>[
                  Component.text('$chunkX,$chunkZ'),
                ]),
              if (showCellDetails && cell != null)
                dom.strong(classes: 'reactor-heatmap-cell-score', <Widget>[
                  Component.text(cell.score.toStringAsFixed(1)),
                ]),
              if (showEmptyLabel && cell == null)
                dom.span(classes: 'reactor-heatmap-cell-empty-label', <Widget>[
                  Component.text(reactorText(ReactorText.heatmapNoData)),
                ]),
            ],
          ),
        );
      }
    }

    final List<Widget> xTicks = <Widget>[];
    for (int column = 0; column < columns; column++) {
      final int chunkX = grid.originChunkX + column * grid.cellSizeChunks;
      final bool center =
          grid.centerChunkX >= chunkX &&
          grid.centerChunkX < chunkX + grid.cellSizeChunks;
      xTicks.add(
        dom.span(
          classes: center ? 'is-center' : null,
          attributes: <String, String>{'data-axis-x': chunkX.toString()},
          <Widget>[
            if (_showTick(column, columns, center))
              Component.text(chunkX.toString()),
          ],
        ),
      );
    }

    final List<Widget> zTicks = <Widget>[];
    for (int row = 0; row < rows; row++) {
      final int chunkZ = grid.originChunkZ + row * grid.cellSizeChunks;
      final bool center =
          grid.centerChunkZ >= chunkZ &&
          grid.centerChunkZ < chunkZ + grid.cellSizeChunks;
      zTicks.add(
        dom.span(
          classes: center ? 'is-center' : null,
          attributes: <String, String>{'data-axis-z': chunkZ.toString()},
          <Widget>[
            if (_showTick(row, rows, center)) Component.text(chunkZ.toString()),
          ],
        ),
      );
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

    final Widget? borderOverlay = _worldBorderOverlay(grid);
    final Widget gridStage = dom.div(
      classes: 'reactor-heatmap-grid-stage',
      styles: dom.Styles(
        raw: <String, String>{'aspect-ratio': '$columns / $rows'},
      ),
      <Widget>[gridWidget, ?borderOverlay],
    );

    final Widget coordinatePlane = dom.div(
      classes: 'reactor-heatmap-plane',
      attributes: <String, String>{
        'dir': 'ltr',
        'data-origin-x': grid.originChunkX.toString(),
        'data-origin-z': grid.originChunkZ.toString(),
        'data-center-x': grid.centerChunkX.toString(),
        'data-center-z': grid.centerChunkZ.toString(),
        'data-cell-size': grid.cellSizeChunks.toString(),
      },
      <Widget>[
        dom.code(classes: 'reactor-heatmap-axis-corner', <Widget>[
          Component.text('Z \\ X'),
        ]),
        dom.div(
          classes: 'reactor-heatmap-axis reactor-heatmap-axis-x',
          styles: dom.Styles(
            raw: <String, String>{
              'grid-template-columns': 'repeat($columns, minmax(0, 1fr))',
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
        gridStage,
      ],
    );

    final Widget inspector = _selectedInspector(
      grid,
      selectedX,
      selectedZ,
      selectedCell,
    );
    final Widget legend = dom.div(classes: 'reactor-heatmap-legend', <Widget>[
      dom.span(<Widget>[
        Component.text(
          reactorText(ReactorText.commonLowValue, <String, Object?>{
            'value': grid.min.toStringAsFixed(2),
          }),
        ),
      ]),
      dom.span(classes: 'reactor-heatmap-legend-scale', const <Widget>[]),
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
        dom.span(<Widget>[
          Component.text(reactorText(ReactorText.heatmapInteractionHint)),
        ]),
      ]),
      dom.div(classes: 'reactor-heatmap-canvas', <Widget>[coordinatePlane]),
      inspector,
      legend,
    ]);
  }

  Widget _selectedInspector(
    HeatmapGrid grid,
    int chunkX,
    int chunkZ,
    HeatmapCell? cell,
  ) {
    final int size = cell?.sizeChunks ?? grid.cellSizeChunks;
    final int maximumChunkX = chunkX + size - 1;
    final int maximumChunkZ = chunkZ + size - 1;
    final int minimumBlockX = chunkX * 16;
    final int minimumBlockZ = chunkZ * 16;
    final int maximumBlockX = (chunkX + size) * 16 - 1;
    final int maximumBlockZ = (chunkZ + size) * 16 - 1;
    final String chunks = size == 1
        ? reactorText(ReactorText.heatmapChunkCoordinate, <String, Object?>{
            'x': chunkX,
            'z': chunkZ,
          })
        : reactorText(ReactorText.heatmapChunkRange, <String, Object?>{
            'minX': chunkX,
            'maxX': maximumChunkX,
            'minZ': chunkZ,
            'maxZ': maximumChunkZ,
          });
    return dom.div(
      classes: 'reactor-heatmap-inspector',
      attributes: const <String, String>{'aria-live': 'polite'},
      <Widget>[
        dom.strong(<Widget>[Component.text(chunks)]),
        dom.code(<Widget>[
          Component.text(
            reactorText(ReactorText.heatmapBlockRange, <String, Object?>{
              'minX': minimumBlockX,
              'maxX': maximumBlockX,
              'minZ': minimumBlockZ,
              'maxZ': maximumBlockZ,
            }),
          ),
        ]),
        dom.code(<Widget>[
          Component.text(
            'MCA r.${_floorDiv(chunkX, 32)}.${_floorDiv(chunkZ, 32)}',
          ),
        ]),
        if (cell == null)
          dom.span(<Widget>[
            Component.text(reactorText(ReactorText.heatmapNoLoadedSamples)),
          ]),
        if (cell != null)
          dom.span(<Widget>[
            Component.text(
              reactorText(ReactorText.heatmapStatistics, <String, Object?>{
                'peak': cell.score.toStringAsFixed(2),
                'average': cell.averageScore.toStringAsFixed(2),
                'samples': cell.samples,
              }),
            ),
          ]),
      ],
    );
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
        'aria-label':
            reactorText(ReactorText.heatmapWorldBorderLabel, <String, Object?>{
              'x': border.centerBlockX,
              'z': border.centerBlockZ,
              'size': border.sizeBlocks,
            }),
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
