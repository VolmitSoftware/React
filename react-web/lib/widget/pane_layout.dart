library;

enum ReactorPaneSide { rail, inspector }

class ReactorPaneLayout {
  final double railWidth;
  final double inspectorWidth;

  const ReactorPaneLayout({
    this.railWidth = defaultRailWidth,
    this.inspectorWidth = defaultInspectorWidth,
  });

  static const double defaultRailWidth = 240;
  static const double defaultInspectorWidth = 320;
  static const double minRailWidth = 200;
  static const double maxRailWidth = 420;
  static const double minInspectorWidth = 280;
  static const double maxInspectorWidth = 520;
  static const double minWorkspaceWidth = 420;
  static const double splitterWidth = 6;
  static const double keyStep = 8;
  static const double coarseKeyStep = 40;

  double widthOf(ReactorPaneSide side) =>
      side == ReactorPaneSide.rail ? railWidth : inspectorWidth;

  static double minOf(ReactorPaneSide side) =>
      side == ReactorPaneSide.rail ? minRailWidth : minInspectorWidth;

  static double maxOf(ReactorPaneSide side) =>
      side == ReactorPaneSide.rail ? maxRailWidth : maxInspectorWidth;

  static double defaultOf(ReactorPaneSide side) =>
      side == ReactorPaneSide.rail ? defaultRailWidth : defaultInspectorWidth;

  static ReactorPaneSide otherOf(ReactorPaneSide side) =>
      side == ReactorPaneSide.rail
      ? ReactorPaneSide.inspector
      : ReactorPaneSide.rail;

  static String variableOf(ReactorPaneSide side) =>
      side == ReactorPaneSide.rail ? '--reactor-rail' : '--reactor-inspector';

  static double widthForEdge(
    ReactorPaneSide side,
    double edgeX,
    double viewportWidth,
  ) => side == ReactorPaneSide.rail
      ? edgeX
      : viewportWidth - edgeX - splitterWidth;

  double clampWidth(
    ReactorPaneSide side,
    double raw, {
    double viewportWidth = 0,
  }) {
    if (!raw.isFinite) return widthOf(side);
    final double low = minOf(side);
    double high = maxOf(side);
    if (viewportWidth > 0) {
      final double available =
          viewportWidth -
          widthOf(otherOf(side)) -
          minWorkspaceWidth -
          (splitterWidth * 2);
      if (available < high) high = available;
    }
    if (high < low) high = low;
    if (raw < low) return low;
    if (raw > high) return high;
    return raw;
  }

  ReactorPaneLayout withWidth(
    ReactorPaneSide side,
    double raw, {
    double viewportWidth = 0,
  }) {
    final double width = clampWidth(side, raw, viewportWidth: viewportWidth);
    return side == ReactorPaneSide.rail
        ? ReactorPaneLayout(railWidth: width, inspectorWidth: inspectorWidth)
        : ReactorPaneLayout(railWidth: railWidth, inspectorWidth: width);
  }

  ReactorPaneLayout nudged(
    ReactorPaneSide side,
    double delta, {
    double viewportWidth = 0,
  }) => withWidth(side, widthOf(side) + delta, viewportWidth: viewportWidth);

  ReactorPaneLayout resetSide(ReactorPaneSide side) =>
      withWidth(side, defaultOf(side));

  @override
  bool operator ==(Object other) =>
      other is ReactorPaneLayout &&
      other.railWidth == railWidth &&
      other.inspectorWidth == inspectorWidth;

  @override
  int get hashCode => Object.hash(railWidth, inspectorWidth);
}
