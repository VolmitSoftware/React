library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr_test/server_test.dart';

import 'package:react_web/model/action_descriptor.dart';
import 'package:react_web/model/config_tree.dart';
import 'package:react_web/model/control_item.dart';
import 'package:react_web/model/knob.dart';
import 'package:react_web/model/role_info.dart';
import 'package:react_web/model/world_settings.dart';
import 'package:react_web/screen/actions.dart';
import 'package:react_web/screen/config_editor.dart';
import 'package:react_web/screen/governors.dart';
import 'package:react_web/screen/logs.dart';
import 'package:react_web/screen/optimization.dart';
import 'package:react_web/screen/tweaks.dart';
import 'package:react_web/screen/world_overrides.dart';
import 'package:react_web/state/actions_controller.dart';

const ShadcnStylesheet _sheet = ShadcnStylesheet(theme: ShadcnTheme.midnight);

const RoleInfo _adminRole = RoleInfo(
  role: 'admin',
  scopes: <String>['read', 'op:execute', 'admin'],
);

const ControlItem _control = ControlItem(
  id: 'runtime-control',
  name: 'Runtime Control',
  category: 'Runtime',
  enabled: true,
  description: 'A mutable runtime control.',
  knobs: <Knob>[
    Knob(key: 'limit', label: 'Limit', type: KnobType.intType, value: 4),
  ],
);

const ActionDescriptor _action = ActionDescriptor(
  id: 'purge',
  name: 'Purge',
  description: 'Purges runtime state.',
  destructive: true,
  params: <ActionParam>[
    ActionParam(
      key: 'limit',
      label: 'Limit',
      type: KnobType.intType,
      required: true,
      defaultValue: 4,
    ),
  ],
);

Widget _wrap(Widget child) =>
    ArcaneThemeProvider(stylesheet: _sheet, child: child);

bool _hasDisabledControl(String body) =>
    body.contains('data-disabled="true"') ||
    RegExp(r'\sdisabled(?:=|>)').hasMatch(body);

void main() {
  group('non-live mutation gating', () {
    testServer('optimization controls render disabled', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrap(
          OptimizationGridView(
            items: const <ControlItem>[_control],
            total: 1,
            enabledCount: 1,
            readOnly: true,
            onToggle: (String _, bool _) {},
            onSetAll: (bool _) {},
            onConfigure: (String _) {},
          ),
        ),
      );
      final DocumentResponse response = await tester.request('/');
      expect(_hasDisabledControl(response.body), isTrue);
    });

    testServer('tweak controls render disabled', (ServerTester tester) async {
      tester.pumpComponent(
        _wrap(
          TweaksListView(
            items: const <ControlItem>[_control],
            readOnly: true,
            onToggle: (String _, bool _) {},
            onKnobChanged: (String _, String _, Object? _) {},
          ),
        ),
      );
      final DocumentResponse response = await tester.request('/');
      expect(_hasDisabledControl(response.body), isTrue);
    });

    testServer('governor toggles render disabled without a live callback', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrap(const GovernorDashboardView(governors: <ControlItem>[_control])),
      );
      final DocumentResponse response = await tester.request('/');
      expect(_hasDisabledControl(response.body), isTrue);
    });

    testServer('world override fields render disabled', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrap(
          WorldOverridesView(
            worlds: const <WorldSettings>[
              WorldSettings(
                name: 'world',
                pressureMode: PressureMode.normal,
                budgetMs: 45,
                panicMs: 50,
                releaseMs: 40,
              ),
            ],
            readOnly: true,
            onSetBudget:
                (
                  String _, {
                  double? budgetMs,
                  double? panicMs,
                  double? releaseMs,
                }) {},
          ),
        ),
      );
      final DocumentResponse response = await tester.request('/');
      expect(_hasDisabledControl(response.body), isTrue);
    });

    testServer('action execution and parameters render disabled', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrap(
          ActionsConsoleView(
            actions: const <ActionDescriptor>[_action],
            recent: const <ActionExecution>[],
            pendingId: _action.id,
            paramValues: const <String, Map<String, Object?>>{},
            role: _adminRole,
            connectionReadOnly: true,
            onExecute: (String _, Map<String, Object?> _, bool _) {},
            onPendingChanged: (String? _) {},
            onParamChanged: (String _, String _, Object? _) {},
          ),
        ),
      );
      final DocumentResponse response = await tester.request('/');
      expect(_hasDisabledControl(response.body), isTrue);
      expect(response.body.contains('Confirm Purge'), isFalse);
    });

    testServer('configuration mutations render disabled', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrap(
          ConfigEditorView(
            tree: const ConfigTree(
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
            pending: const <String, Object?>{},
            connectionReadOnly: true,
            onEdit: (String _, Object? _) {},
            onPreset: (String _) {},
            onApply: () {},
          ),
        ),
      );
      final DocumentResponse response = await tester.request('/');
      expect(_hasDisabledControl(response.body), isTrue);
      expect(response.body.contains('live server connection'), isTrue);
    });

    testServer('console command controls render disabled', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrap(
          LogsView(
            lines: const <String>['[INFO] ready'],
            paused: false,
            levelFilter: 'ALL',
            onPause: null,
            onClear: null,
            onLevelFilter: null,
            command: 'say hello',
            consoleEnabled: false,
            consoleUnavailableMessage:
                'Command execution is disabled until the connection is live.',
            onCommandChanged: (String _) {},
            onExecuteCommand: () {},
          ),
        ),
      );
      final DocumentResponse response = await tester.request('/');
      expect(_hasDisabledControl(response.body), isTrue);
      expect(response.body.contains('connection is live'), isTrue);
    });
  });
}
