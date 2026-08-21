import 'package:react_web/widget/pane_layout.dart';
import 'package:test/test.dart';

void main() {
  group('ReactorPaneLayout', () {
    test('clamps panes to their hard bounds', () {
      const ReactorPaneLayout layout = ReactorPaneLayout();

      expect(
        layout.withWidth(ReactorPaneSide.rail, 100).railWidth,
        ReactorPaneLayout.minRailWidth,
      );
      expect(
        layout.withWidth(ReactorPaneSide.inspector, 900).inspectorWidth,
        ReactorPaneLayout.maxInspectorWidth,
      );
    });

    test('protects the minimum workspace width', () {
      const ReactorPaneLayout layout = ReactorPaneLayout();
      final ReactorPaneLayout resized = layout.withWidth(
        ReactorPaneSide.rail,
        420,
        viewportWidth: 1024,
      );

      expect(resized.railWidth, 272);
    });

    test('maps splitter edges from either side', () {
      expect(
        ReactorPaneLayout.widthForEdge(ReactorPaneSide.rail, 260, 1440),
        260,
      );
      expect(
        ReactorPaneLayout.widthForEdge(ReactorPaneSide.inspector, 1100, 1440),
        334,
      );
    });

    test('resets each pane independently', () {
      const ReactorPaneLayout layout = ReactorPaneLayout(
        railWidth: 320,
        inspectorWidth: 480,
      );

      expect(
        layout.resetSide(ReactorPaneSide.rail),
        const ReactorPaneLayout(railWidth: 240, inspectorWidth: 480),
      );
    });
  });
}
