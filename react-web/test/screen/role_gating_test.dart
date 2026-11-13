/// What each role may change on the Console, Actions, Tuning and Plugins
/// pages. The server checks every call; these keep a viewer from seeing a
/// live control it cannot use.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/jaspr.dart' show Element;
import 'package:jaspr_test/server_test.dart';

import 'package:react_web/model/action_descriptor.dart';
import 'package:react_web/model/control_item.dart';
import 'package:react_web/model/config_tree.dart';
import 'package:react_web/model/knob.dart';
import 'package:react_web/model/role_info.dart';
import 'package:react_web/screen/actions.dart';
import 'package:react_web/screen/config_editor.dart';
import 'package:react_web/screen/logs.dart';
import 'package:react_web/screen/optimization.dart';
import 'package:react_web/screen/pages/plugins_page.dart';
import 'package:react_web/screen/plugin_api_packs.dart';
import 'package:react_web/state/actions_controller.dart';

import 'support/operate_fakes.dart';

const List<ActionDescriptor> _actions = <ActionDescriptor>[
  ActionDescriptor(
    id: 'gc',
    name: 'Force GC',
    description: 'Triggers JVM GC.',
    destructive: false,
  ),
  ActionDescriptor(
    id: 'purge',
    name: 'Purge Cache',
    description: 'Clears caches.',
    destructive: true,
  ),
];

/// The merged Features list: an ordinary feature and a governor.
final List<ControlItem> _features = <ControlItem>[
  controlItem('mob-stacking', name: 'Mob Stacking'),
  controlItem('random-tick-governor', name: 'Random Tick Governor'),
];

const ConfigTree _tree = ConfigTree(
  sections: <ConfigSection>[
    ConfigSection(
      name: 'Runtime',
      nodes: <Knob>[
        Knob(key: 'limit', label: 'Limit', type: KnobType.intType, value: 4),
      ],
    ),
  ],
);

Widget _wrap(Widget child) =>
    ArcaneThemeProvider(stylesheet: testSheet, child: child);

T _one<T>() => find.byType(T).evaluate().single.component as T;

bool _hasCommandBox() => find
    .byType(TextInput)
    .evaluate()
    .any(
      (Element element) =>
          (element.component as TextInput).attributes?['aria-label'] ==
          'Command',
    );

FakeServer _server() => FakeServer(
  client: OperateClient(
    features: List<ControlItem>.of(_features),
    tweaks: List<ControlItem>.of(_features),
    actions: _actions,
    config: _tree,
    logs: const <String>['[INFO] ready'],
  ),
  socket: FakeLogSocket(),
);

void main() {
  group('Actions', () {
    testServer('operator: destructive Run is off and says why', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrap(
          ActionsView(
            actions: _actions,
            recent: const <ActionExecution>[],
            role: operatorRole,
            onRun: (ActionDescriptor _) {},
          ),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.body, contains('Requires admin role'));
      expect(RegExp('data-disabled="true"').allMatches(res.body), hasLength(1));
    });

    testServer('admin: every Run is on', (ServerTester tester) async {
      tester.pumpComponent(
        _wrap(
          ActionsView(
            actions: _actions,
            recent: const <ActionExecution>[],
            role: adminRole,
            onRun: (ActionDescriptor _) {},
          ),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.body, isNot(contains('Requires admin role')));
      expect(res.body, isNot(contains('data-disabled="true"')));
    });

    for (final (RoleInfo role, bool runs) in <(RoleInfo, bool)>[
      (viewerRole, false),
      (operatorRole, true),
      (adminRole, true),
    ]) {
      testComponents('${role.role}: may run actions = $runs', (
        ComponentTester tester,
      ) async {
        tester.pumpComponent(
          serverScopes(_server(), const ActionsScreen(), role: role),
        );
        await tester.pump();
        expect(_one<ActionsView>().onRun != null, runs);
      });
    }
  });

  group('Features and tweaks', () {
    testServer('a list without a toggle renders its switches disabled', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrap(
          ControlListView(
            items: _features,
            itemLabel: 'Feature',
            emptyMessage: 'None',
            category: governorsFilter,
            governors: true,
          ),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.body, contains('Random Tick Governor'));
      expect(res.body, isNot(contains('Mob Stacking')));
      expect(res.body, contains('data-disabled="true"'));
    });

    for (final (RoleInfo role, bool writes) in <(RoleInfo, bool)>[
      (viewerRole, false),
      (operatorRole, true),
      (adminRole, true),
    ]) {
      testComponents(
        '${role.role}: governors and features writable = $writes',
        (ComponentTester tester) async {
          tester.pumpComponent(
            serverScopes(
              _server(),
              const ControlListScreen.features(),
              role: role,
            ),
          );
          await tester.pump();
          final ControlListView view = _one<ControlListView>();
          expect(view.governors, isTrue);
          expect(
            filterControlItems(view.items, category: governorsFilter),
            hasLength(1),
          );
          expect(view.onToggle != null, writes);
          expect(view.onSetAll != null, writes);
        },
      );

      testComponents('${role.role}: tweaks writable = $writes', (
        ComponentTester tester,
      ) async {
        tester.pumpComponent(
          serverScopes(_server(), const ControlListScreen.tweaks(), role: role),
        );
        await tester.pump();
        expect(_one<ControlListView>().onToggle != null, writes);
      });
    }
  });

  group('Config', () {
    for (final (RoleInfo role, bool writes) in <(RoleInfo, bool)>[
      (viewerRole, false),
      (operatorRole, false),
      (adminRole, true),
    ]) {
      testComponents('${role.role}: may change the config = $writes', (
        ComponentTester tester,
      ) async {
        tester.pumpComponent(
          serverScopes(_server(), const ConfigEditorScreen(), role: role),
        );
        await tester.pump();
        expect(_one<ConfigPresetsCard>().onPreset != null, writes);
        expect(_one<ConfigSectionsCard>().onEdit != null, writes);
      });
    }
  });

  group('Console', () {
    for (final (RoleInfo role, bool reads, bool sends)
        in <(RoleInfo, bool, bool)>[
          (viewerRole, false, false),
          (operatorRole, false, false),
          (adminRole, true, true),
        ]) {
      testComponents('${role.role}: reads $reads, sends commands $sends', (
        ComponentTester tester,
      ) async {
        tester.pumpComponent(
          serverScopes(_server(), const LogsScreen(), role: role),
        );
        await tester.pump();
        expect(find.byType(LogViewport).evaluate().isNotEmpty, reads);
        expect(_hasCommandBox(), sends);
      });
    }
  });

  group('Plugins', () {
    for (final (RoleInfo role, bool admin) in <(RoleInfo, bool)>[
      (viewerRole, false),
      (operatorRole, false),
      (adminRole, true),
    ]) {
      testComponents('${role.role}: API packs tab and changes = $admin', (
        ComponentTester tester,
      ) async {
        tester.pumpComponent(
          serverScopes(_server(), const PluginsPage(), role: role),
        );
        await tester.pump();
        expect(find.text('API packs').evaluate().isNotEmpty, admin);
      });

      testComponents('${role.role}: may change packs = $admin', (
        ComponentTester tester,
      ) async {
        tester.pumpComponent(
          serverScopes(_server(), const PluginApiPacksScreen(), role: role),
        );
        await tester.pump();
        expect(_one<PluginApiCatalogView>().onNew != null, admin);
        expect(_one<PluginApiCatalogView>().onDelete != null, admin);
      });
    }
  });
}
