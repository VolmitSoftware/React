/// Without a live connection nothing on the Console, Actions, Tuning and
/// Plugins pages writes to the server, whatever the role.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/jaspr.dart' show Element;
import 'package:jaspr_test/jaspr_test.dart';

import 'package:react_web/model/action_descriptor.dart';
import 'package:react_web/model/config_tree.dart';
import 'package:react_web/model/control_item.dart';
import 'package:react_web/model/knob.dart';
import 'package:react_web/model/world_settings.dart';
import 'package:react_web/screen/actions.dart';
import 'package:react_web/screen/config_editor.dart';
import 'package:react_web/screen/logs.dart';
import 'package:react_web/screen/optimization.dart';
import 'package:react_web/screen/plugin_api_packs.dart';
import 'package:react_web/screen/world_overrides.dart';
import 'package:react_web/state/connection_manager.dart';

import 'support/operate_fakes.dart';

T _one<T>() => find.byType(T).evaluate().single.component as T;

FakeServer _server() => FakeServer(
  client: OperateClient(
    features: <ControlItem>[controlItem('random-tick-governor')],
    tweaks: <ControlItem>[controlItem('fast-snow')],
    worlds: <WorldSettings>[worldSettings('world')],
    actions: const <ActionDescriptor>[
      ActionDescriptor(
        id: 'gc',
        name: 'Force GC',
        description: '',
        destructive: false,
      ),
    ],
    config: const ConfigTree(
      sections: <ConfigSection>[
        ConfigSection(
          name: 'Runtime',
          nodes: <Knob>[
            Knob(
              key: 'limit',
              label: 'Limit',
              type: KnobType.intType,
              value: 4,
            ),
          ],
        ),
      ],
    ),
    logs: const <String>['[INFO] ready'],
  ),
  socket: FakeLogSocket(),
);

void main() {
  for (final ConnState state in <ConnState>[
    ConnState.connecting,
    ConnState.degraded,
    ConnState.offline,
  ]) {
    group('${state.name}: nothing writes', () {
      testComponents('features, including governors', (
        ComponentTester tester,
      ) async {
        tester.pumpComponent(
          serverScopes(
            _server(),
            const ControlListScreen.features(),
            state: state,
          ),
        );
        await tester.pump();
        expect(_one<ControlListView>().onToggle, isNull);
        expect(_one<ControlListView>().onSetAll, isNull);
      });

      testComponents('tweaks', (ComponentTester tester) async {
        tester.pumpComponent(
          serverScopes(
            _server(),
            const ControlListScreen.tweaks(),
            state: state,
          ),
        );
        await tester.pump();
        expect(_one<ControlListView>().onToggle, isNull);
      });

      testComponents('world budgets', (ComponentTester tester) async {
        tester.pumpComponent(
          serverScopes(_server(), const WorldOverridesScreen(), state: state),
        );
        await tester.pump();
        _one<WorldOverridesView>().onEdit!('world');
        await tester.pump();
        expect(_one<WorldBudgetSheet>().onStage, isNull);
        expect(_one<WorldBudgetSheet>().onSave, isNull);
      });

      testComponents('actions', (ComponentTester tester) async {
        tester.pumpComponent(
          serverScopes(_server(), const ActionsScreen(), state: state),
        );
        await tester.pump();
        expect(_one<ActionsView>().onRun, isNull);
      });

      testComponents('config', (ComponentTester tester) async {
        tester.pumpComponent(
          serverScopes(_server(), const ConfigEditorScreen(), state: state),
        );
        await tester.pump();
        expect(_one<ConfigPresetsCard>().onPreset, isNull);
        expect(_one<ConfigSectionsCard>().onEdit, isNull);
        expect(_one<ConfigSectionsCard>().onSave, isNull);
      });

      testComponents('console commands', (ComponentTester tester) async {
        tester.pumpComponent(
          serverScopes(_server(), const LogsScreen(), state: state),
        );
        await tester.pump();
        final Iterable<TextInput> boxes = find
            .byType(TextInput)
            .evaluate()
            .map((Element element) => element.component as TextInput)
            .where(
              (TextInput input) => input.attributes?['aria-label'] == 'Command',
            );
        expect(boxes.single.disabled, isTrue);
      });

      testComponents('plugin packs', (ComponentTester tester) async {
        tester.pumpComponent(
          serverScopes(_server(), const PluginApiPacksScreen(), state: state),
        );
        await tester.pump();
        expect(_one<PluginApiCatalogView>().onNew, isNull);
        expect(_one<PluginApiCatalogView>().onEdit, isNull);
        expect(_one<PluginApiCatalogView>().onDelete, isNull);
      });
    });
  }
}
