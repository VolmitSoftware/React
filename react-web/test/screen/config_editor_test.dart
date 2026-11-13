library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/jaspr.dart' show GlobalStateKey;
import 'package:jaspr_test/server_test.dart';

import 'package:react_web/localization/reactor_localizations.dart';
import 'package:react_web/model/config_tree.dart';
import 'package:react_web/model/knob.dart';
import 'package:react_web/screen/config_editor.dart';
import 'package:react_web/ui/kit/mode.dart';

import 'support/operate_fakes.dart';

ConfigTree _tree(int tickRate) => ConfigTree(
  sections: <ConfigSection>[
    ConfigSection(
      name: 'Performance',
      nodes: <Knob>[
        Knob(
          key: 'tick-rate',
          label: 'Tick Rate',
          type: KnobType.intType,
          value: tickRate,
        ),
      ],
    ),
    const ConfigSection(
      name: 'Entity',
      nodes: <Knob>[
        Knob(
          key: 'max-entities',
          label: 'Max Entities',
          type: KnobType.intType,
          value: 512,
        ),
      ],
    ),
  ],
);

Widget _wrap(Widget child) =>
    ArcaneThemeProvider(stylesheet: testSheet, child: child);

ConfigSectionsCard _sections() =>
    find.byType(ConfigSectionsCard).evaluate().single.component
        as ConfigSectionsCard;

ConfigPresetsCard _presets() =>
    find.byType(ConfigPresetsCard).evaluate().single.component
        as ConfigPresetsCard;

void main() {
  group('ConfigSectionsCard', () {
    testServer('every section with its settings, unsaved values shown', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrap(
          ConfigSectionsCard(
            tree: _tree(20),
            pending: const <String, Object?>{'tick-rate': 25},
            onEdit: (String _, Object? _) {},
            onSave: () {},
            onDiscard: () {},
          ),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.body, contains('Performance'));
      expect(res.body, contains('Max Entities'));
      expect(res.body, contains('value="25"'));
      expect(res.body, contains('Save'));
      expect(res.body, contains(ReactorText.commonCancel.english));
      expect(res.body, isNot(contains('data-disabled="true"')));
    });

    testServer('section names from react.toml read in sentence case', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrap(
          ConfigSectionsCard(
            tree: const ConfigTree(
              sections: <ConfigSection>[
                ConfigSection(name: 'general', nodes: <Knob>[]),
                ConfigSection(name: 'monitoring', nodes: <Knob>[]),
              ],
            ),
            pending: const <String, Object?>{},
            onEdit: (String _, Object? _) {},
            onSave: () {},
            onDiscard: () {},
          ),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.body, contains('>General<'));
      expect(res.body, contains('>Monitoring<'));
      expect(res.body, isNot(contains('>general<')));
    });

    testServer('Save and Cancel wait for an edit', (ServerTester tester) async {
      tester.pumpComponent(
        _wrap(
          ConfigSectionsCard(
            tree: _tree(20),
            pending: const <String, Object?>{},
            onEdit: (String _, Object? _) {},
            onSave: () {},
            onDiscard: () {},
          ),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(
        RegExp('data-disabled="true"').allMatches(res.body).length,
        greaterThanOrEqualTo(2),
      );
    });

    testServer('an empty tree says so', (ServerTester tester) async {
      tester.pumpComponent(
        _wrap(
          const ConfigSectionsCard(
            tree: ConfigTree(),
            pending: <String, Object?>{},
          ),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.body, contains('No configuration sections'));
    });
  });

  group('ConfigEditorScreen', () {
    testServer('without a client it asks for a live connection', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(serverScopes(null, const ConfigEditorScreen()));
      final DocumentResponse res = await tester.request('/');
      expect(res.body, contains('Configuration unavailable'));
    });

    testComponents('Simple shows the presets only', (
      ComponentTester tester,
    ) async {
      tester.pumpComponent(
        serverScopes(
          FakeServer(client: OperateClient(config: _tree(20))),
          const ConfigEditorScreen(),
          mode: ReactorMode.simple,
        ),
      );
      await tester.pump();
      expect(find.byType(ConfigPresetsCard), findsOneComponent);
      expect(find.byType(ConfigSectionsCard), findsNothing);
    });

    testComponents('an operator may not change the config', (
      ComponentTester tester,
    ) async {
      tester.pumpComponent(
        serverScopes(
          FakeServer(client: OperateClient(config: _tree(20))),
          const ConfigEditorScreen(),
          role: operatorRole,
        ),
      );
      await tester.pump();
      expect(_presets().onPreset, isNull);
      expect(_sections().onEdit, isNull);
      expect(_sections().onSave, isNull);
    });

    testComponents('edits wait for Save, then go out together', (
      ComponentTester tester,
    ) async {
      final FakeServer server = FakeServer(
        client: OperateClient(config: _tree(20)),
      );
      tester.pumpComponent(serverScopes(server, const ConfigEditorScreen()));
      await tester.pump();
      _sections().onEdit!('tick-rate', 30);
      await tester.pump();
      expect(_sections().pending, <String, Object?>{'tick-rate': 30});
      expect(server.client.writes('PUT /config'), 0);
      _sections().onSave!();
      await tester.pump();
      expect(server.client.calls, contains('PUT /config'));
    });

    testComponents('a preset over unsaved edits asks first', (
      ComponentTester tester,
    ) async {
      final FakeServer server = FakeServer(
        client: OperateClient(config: _tree(20)),
      );
      tester.pumpComponent(serverScopes(server, const ConfigEditorScreen()));
      await tester.pump();
      _presets().onPreset!('light');
      await tester.pump();
      expect(server.client.calls, contains('POST /config/preset/light'));

      _sections().onEdit!('tick-rate', 30);
      await tester.pump();
      _presets().onPreset!('high');
      await tester.pump();
      expect(server.client.calls, isNot(contains('POST /config/preset/high')));
      expect(_sections().pending, isNotEmpty);
    });

    testComponents('a preset over unsaved edits applies after a yes', (
      ComponentTester tester,
    ) async {
      final FakeServer server = FakeServer(
        client: OperateClient(config: _tree(20)),
      );
      tester.pumpComponent(
        withConfirmHost(serverScopes(server, const ConfigEditorScreen())),
      );
      await tester.pump();
      _sections().onEdit!('tick-rate', 30);
      await tester.pump();
      _presets().onPreset!('high');
      await tester.pump();
      await answerConfirm(tester, yes: true);
      expect(server.client.calls, contains('POST /config/preset/high'));
      expect(_sections().pending, isEmpty);
      await unmount(tester);
    });

    testComponents('switching servers reloads and saves to the new server', (
      ComponentTester tester,
    ) async {
      final FakeServer first = FakeServer(
        client: OperateClient(config: _tree(20)),
      );
      final FakeServer second = FakeServer(
        client: OperateClient(config: _tree(40)),
      );
      final GlobalStateKey<ServerSwitcherState> key =
          GlobalStateKey<ServerSwitcherState>();
      tester.pumpComponent(
        ServerSwitcher(
          key: key,
          servers: <FakeServer>[first, second],
          child: const ConfigEditorScreen(),
        ),
      );
      await tester.pump();
      expect(_sections().tree.node('tick-rate')!.value, 20);
      _sections().onEdit!('tick-rate', 21);
      await tester.pump();

      key.currentState!.show(1);
      await tester.pump();
      expect(_sections().tree.node('tick-rate')!.value, 40);
      expect(_sections().pending, isEmpty);

      _sections().onEdit!('tick-rate', 45);
      await tester.pump();
      _sections().onSave!();
      await tester.pump();
      expect(second.client.calls, contains('PUT /config'));
      expect(first.client.writes('PUT /config'), 0);
    });
  });
}
