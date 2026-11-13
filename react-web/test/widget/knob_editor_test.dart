library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:react_web/theme/reactor_stylesheet.dart';
import 'package:jaspr_test/server_test.dart';

import 'package:react_web/localization/reactor_localizations.dart';
import 'package:react_web/model/knob.dart';
import 'package:react_web/widget/knob_editor.dart';

const ReactorStylesheet _sheet = ReactorStylesheet();

Widget _wrap(Widget child) =>
    ArcaneThemeProvider(stylesheet: _sheet, child: child);

/// The opening tag of the first element whose tag matches [tag] and that
/// carries [attribute].
String _openingTag(String html, String tag, String attribute) =>
    RegExp('<$tag\\b[^>]*$attribute[^>]*>').firstMatch(html)?.group(0) ?? '';

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

  group('KnobEditor fields', () {
    testServer('a number knob is a library field whose label names its box', (
      ServerTester tester,
    ) async {
      const Knob knob = Knob(
        key: 'max-stack',
        label: 'Max Stack',
        type: KnobType.intType,
        value: 64,
        doc: 'Most items per stack',
      );
      tester.pumpComponent(_wrap(const KnobEditor(knob: knob)));
      final String body = (await tester.request('/')).body;
      expect(
        _openingTag(body, 'div', 'data-slot="field"'),
        contains('data-orientation="vertical"'),
      );
      expect(_openingTag(body, 'label', 'for="knob-max-stack"'), isNotEmpty);
      expect(_openingTag(body, 'input', 'id="knob-max-stack"'), isNotEmpty);
      expect(body, isNot(contains('ui-field')));
    });

    testServer(
      'a switch knob is a horizontal field whose label names the switch',
      (ServerTester tester) async {
        const Knob knob = Knob(
          key: 'stack-mobs',
          label: 'Stack Mobs',
          type: KnobType.boolType,
          value: true,
        );
        tester.pumpComponent(_wrap(const KnobEditor(knob: knob)));
        final String body = (await tester.request('/')).body;
        expect(
          _openingTag(body, 'div', 'data-slot="field"'),
          contains('data-orientation="horizontal"'),
        );
        expect(_openingTag(body, 'label', 'for="knob-stack-mobs"'), isNotEmpty);
        expect(
          _openingTag(body, 'button', 'role="switch"'),
          contains('id="knob-stack-mobs"'),
        );
      },
    );

    testServer('a setting the server changed says so inside its field', (
      ServerTester tester,
    ) async {
      const Knob knob = Knob(
        key: 'view-distance',
        label: 'View distance',
        type: KnobType.intType,
        value: 8,
      );
      tester.pumpComponent(
        _wrap(const KnobEditor(knob: knob, changedOnServer: true)),
      );
      final String body = (await tester.request('/')).body;
      final int field = body.indexOf('data-slot="field"');
      final int warning = body.indexOf(
        ReactorText.optimizationChangedOnServer.english,
      );
      expect(field, greaterThanOrEqualTo(0));
      expect(warning, greaterThan(body.indexOf('id="knob-view-distance"')));
    });
  });
}
