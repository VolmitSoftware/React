library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr_test/server_test.dart';

import 'package:react_web/model/control_item.dart';
import 'package:react_web/model/knob.dart';
import 'package:react_web/widget/config_sheet.dart';

const ShadcnStylesheet _sheet = ShadcnStylesheet(theme: ShadcnTheme.midnight);

Widget _wrap(Widget child) =>
    ArcaneThemeProvider(stylesheet: _sheet, child: child);

void main() {
  group('ConfigSheetBody', () {
    testServer(
      'renders item name, description, and all knob labels and values',
      (ServerTester tester) async {
        const ControlItem item = ControlItem(
          id: 'mob-stacking',
          name: 'Mob Stacking',
          category: 'optimization',
          enabled: true,
          description: 'Combine nearby mobs',
          knobs: <Knob>[
            Knob(
              key: 'stack-mobs',
              label: 'Stack Mobs',
              type: KnobType.boolType,
              value: true,
            ),
            Knob(
              key: 'max-stack',
              label: 'Max Stack',
              type: KnobType.intType,
              value: 64,
            ),
          ],
        );
        tester.pumpComponent(_wrap(ConfigSheetBody(item: item)));
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('Mob Stacking'),
          isTrue,
          reason: 'item name must appear in rendered HTML',
        );
        expect(
          res.body.contains('Combine nearby mobs'),
          isTrue,
          reason: 'item description must appear in rendered HTML',
        );
        expect(
          res.body.contains('Stack Mobs'),
          isTrue,
          reason: 'bool knob label must appear in rendered HTML',
        );
        expect(
          res.body.contains('Max Stack'),
          isTrue,
          reason: 'int knob label must appear in rendered HTML',
        );
        expect(
          res.body.contains('64'),
          isTrue,
          reason: 'int knob value must appear in rendered HTML',
        );
      },
    );

    testServer('renders empty state when item has no knobs', (
      ServerTester tester,
    ) async {
      const ControlItem item = ControlItem(
        id: 'no-knobs',
        name: 'Plain Item',
        category: 'general',
        enabled: true,
        description: '',
      );
      tester.pumpComponent(_wrap(ConfigSheetBody(item: item)));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('No configurable options'),
        isTrue,
        reason: 'empty state title must appear when item has no knobs',
      );
    });
  });
}
