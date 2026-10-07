import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr_test/jaspr_test.dart';
import 'package:react_web/widget/row_window.dart';

Widget _window(List<int> rows, {bool latest = false}) => ArcaneThemeProvider(
  stylesheet: const ShadcnStylesheet(theme: ShadcnTheme.midnight),
  child: RowWindow<int>(
    rows: rows,
    latest: latest,
    pageSize: 100,
    builder: (List<int> visible) => Widget.fragment(<Widget>[
      for (final int row in visible) Text('row-$row'),
    ]),
  ),
);

void main() {
  testComponents('bounds rows and keeps every page reachable', (
    ComponentTester tester,
  ) async {
    tester.pumpComponent(
      _window(List<int>.generate(250, (int index) => index)),
    );
    expect(find.text('row-0'), findsOneComponent);
    expect(find.text('row-99'), findsOneComponent);
    expect(find.text('row-100'), findsNothing);
    await tester.click(
      find.ancestor(of: find.text('3'), matching: find.tag('button')),
    );
    expect(find.text('row-249'), findsOneComponent);
    expect(find.text('row-0'), findsNothing);
    tester.pumpComponent(_window(<int>[0, 1]));
    expect(find.text('row-0'), findsOneComponent);
    expect(find.text('row-1'), findsOneComponent);
  });

  testComponents('log windows start at the newest page', (
    ComponentTester tester,
  ) async {
    tester.pumpComponent(
      _window(List<int>.generate(250, (int index) => index), latest: true),
    );
    expect(find.text('row-249'), findsOneComponent);
    expect(find.text('row-0'), findsNothing);
    await tester.click(
      find.ancestor(of: find.text('1'), matching: find.tag('button')),
    );
    expect(find.text('row-0'), findsOneComponent);
  });
}
