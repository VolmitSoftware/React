library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;

import 'pane_dom.dart';
import 'pane_layout.dart';

class ReactorPaneSplitter extends StatefulWidget {
  final ReactorPaneSide side;
  final ReactorPaneLayout layout;
  final void Function(ReactorPaneLayout layout) onCommit;

  const ReactorPaneSplitter({
    required this.side,
    required this.layout,
    required this.onCommit,
    super.key,
  });

  String get handleId => side == ReactorPaneSide.rail
      ? 'reactor-splitter-rail'
      : 'reactor-splitter-inspector';

  String get label => side == ReactorPaneSide.rail
      ? 'Resize server navigation'
      : 'Resize server inspector';

  @override
  State<ReactorPaneSplitter> createState() => _ReactorPaneSplitterState();
}

class _ReactorPaneSplitterState extends State<ReactorPaneSplitter> {
  void Function()? _uninstall;
  ReactorPaneLayout? _dragLayout;
  bool _installPending = false;
  bool _disposed = false;

  ReactorPaneLayout get _current => _dragLayout ?? component.layout;

  @override
  void dispose() {
    _disposed = true;
    _uninstall?.call();
    _uninstall = null;
    super.dispose();
  }

  void _scheduleInstall() {
    if (_installPending || _disposed || _uninstall != null) return;
    _installPending = true;
    context.binding.addPostFrameCallback(() {
      _installPending = false;
      if (_disposed || _uninstall != null) return;
      _uninstall = installPaneSplitter(
        handleId: component.handleId,
        onDragMove: _onDragMove,
        onDragEnd: _onDragEnd,
        onReset: _onReset,
        onKey: _onKey,
      );
    });
  }

  void _onDragMove(double edgeX) {
    final double viewport = paneViewportWidth();
    final double width = ReactorPaneLayout.widthForEdge(
      component.side,
      edgeX,
      viewport,
    );
    final ReactorPaneLayout next = _current.withWidth(
      component.side,
      width,
      viewportWidth: viewport,
    );
    _dragLayout = next;
    _paint(next);
  }

  void _onDragEnd() {
    final ReactorPaneLayout? dragged = _dragLayout;
    _dragLayout = null;
    if (dragged == null || dragged == component.layout) return;
    component.onCommit(dragged);
  }

  void _onReset() {
    final ReactorPaneLayout next = _current.resetSide(component.side);
    _dragLayout = null;
    _paint(next);
    if (next != component.layout) component.onCommit(next);
  }

  void _onKey(String key, bool shift) {
    final double viewport = paneViewportWidth();
    final ReactorPaneSide side = component.side;
    final double step = shift
        ? ReactorPaneLayout.coarseKeyStep
        : ReactorPaneLayout.keyStep;
    final double growth = side == ReactorPaneSide.rail ? step : -step;
    final ReactorPaneLayout base = _current;
    final ReactorPaneLayout next = switch (key) {
      'ArrowRight' => base.nudged(side, growth, viewportWidth: viewport),
      'ArrowLeft' => base.nudged(side, -growth, viewportWidth: viewport),
      'Home' => base.withWidth(
        side,
        ReactorPaneLayout.minOf(side),
        viewportWidth: viewport,
      ),
      'End' => base.withWidth(
        side,
        ReactorPaneLayout.maxOf(side),
        viewportWidth: viewport,
      ),
      _ => base,
    };
    if (next == base) return;
    _dragLayout = null;
    _paint(next);
    component.onCommit(next);
  }

  void _paint(ReactorPaneLayout layout) {
    final double width = layout.widthOf(component.side);
    writePaneVariable(ReactorPaneLayout.variableOf(component.side), width);
    writePaneAria(component.handleId, width);
  }

  @override
  Widget build(BuildContext context) {
    _scheduleInstall();
    final ReactorPaneSide side = component.side;
    final double width = component.layout.widthOf(side);
    return dom.div(
      id: component.handleId,
      classes: 'reactor-splitter',
      attributes: <String, String>{
        'role': 'separator',
        'aria-orientation': 'vertical',
        'aria-label': component.label,
        'aria-valuemin': ReactorPaneLayout.minOf(side).round().toString(),
        'aria-valuemax': ReactorPaneLayout.maxOf(side).round().toString(),
        'aria-valuenow': width.round().toString(),
        'aria-valuetext': '${width.round()} pixels',
        'tabindex': '0',
      },
      const <Widget>[
        dom.span(
          classes: 'reactor-splitter-grip',
          attributes: <String, String>{'aria-hidden': 'true'},
          <Widget>[],
        ),
      ],
    );
  }
}
