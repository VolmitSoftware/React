library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr_test/server_test.dart';

import 'package:react_web/model/knob.dart';
import 'package:react_web/widget/knob_editor.dart';

const ShadcnStylesheet _sheet = ShadcnStylesheet(theme: ShadcnTheme.midnight);

Widget _wrap(Widget child) =>
    ArcaneThemeProvider(stylesheet: _sheet, child: child);

void main() {
  group('KnobEditor rendering', () {
    testServer('bool knob renders label and doc text', (
      ServerTester tester,
    ) async {
      const Knob knob = Knob(
        key: 'stack-mobs',
        label: 'Stack Mobs',
        type: KnobType.boolType,
        value: true,
        doc: 'Combine nearby mobs',
      );
      tester.pumpComponent(_wrap(KnobEditor(knob: knob)));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Stack Mobs'),
        isTrue,
        reason: 'bool knob label must appear in rendered HTML',
      );
      expect(
        res.body.contains('Combine nearby mobs'),
        isTrue,
        reason: 'bool knob doc must appear as helper text in rendered HTML',
      );
    });

    testServer('int knob renders label and value', (ServerTester tester) async {
      const Knob knob = Knob(
        key: 'max-stack',
        label: 'Max Stack',
        type: KnobType.intType,
        value: 64,
      );
      tester.pumpComponent(_wrap(KnobEditor(knob: knob)));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
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
    });

    testServer('double knob renders label and value', (
      ServerTester tester,
    ) async {
      const Knob knob = Knob(
        key: 'threshold',
        label: 'Threshold',
        type: KnobType.doubleType,
        value: 1.5,
      );
      tester.pumpComponent(_wrap(KnobEditor(knob: knob)));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Threshold'),
        isTrue,
        reason: 'double knob label must appear in rendered HTML',
      );
      expect(
        res.body.contains('1.5'),
        isTrue,
        reason: 'double knob value must appear in rendered HTML',
      );
    });

    testServer('string knob renders label and value', (
      ServerTester tester,
    ) async {
      const Knob knob = Knob(
        key: 'mode-name',
        label: 'Mode Name',
        type: KnobType.stringType,
        value: 'aggressive',
      );
      tester.pumpComponent(_wrap(KnobEditor(knob: knob)));
      final DocumentResponse res = await tester.request('/');
      expect(res.statusCode, equals(200));
      expect(
        res.body.contains('Mode Name'),
        isTrue,
        reason: 'string knob label must appear in rendered HTML',
      );
      expect(
        res.body.contains('aggressive'),
        isTrue,
        reason: 'string knob value must appear in rendered HTML',
      );
    });

    testServer(
      'enum knob renders label and all option values as native select options',
      (ServerTester tester) async {
        const Knob knob = Knob(
          key: 'strategy',
          label: 'Strategy',
          type: KnobType.enumType,
          value: 'B',
          options: <String>['A', 'B', 'C'],
        );
        tester.pumpComponent(_wrap(KnobEditor(knob: knob)));
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('Strategy'),
          isTrue,
          reason: 'enum knob label must appear in rendered HTML',
        );
        expect(
          res.body.contains('A'),
          isTrue,
          reason: 'enum option A must appear as native select option',
        );
        expect(
          res.body.contains('B'),
          isTrue,
          reason: 'enum option B must appear as native select option',
        );
        expect(
          res.body.contains('C'),
          isTrue,
          reason: 'enum option C must appear as native select option',
        );
      },
    );
  });
}
