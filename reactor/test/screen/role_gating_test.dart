library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr_test/server_test.dart';
import 'package:test/test.dart';

import 'package:reactor/model/action_descriptor.dart';
import 'package:reactor/model/control_item.dart';
import 'package:reactor/model/role_info.dart';
import 'package:reactor/screen/actions.dart';
import 'package:reactor/screen/optimization.dart';
import 'package:reactor/state/actions_controller.dart';
import 'package:reactor/widget/role_badge.dart';

const ShadcnStylesheet _sheet = ShadcnStylesheet(theme: ShadcnTheme.midnight);

Widget _wrap(Widget child) => ArcaneThemeProvider(stylesheet: _sheet, child: child);

final RoleInfo _viewerRole = const RoleInfo(
  role: 'viewer',
  scopes: <String>['read'],
);

final RoleInfo _operatorRole = const RoleInfo(
  role: 'operator',
  scopes: <String>['read', 'op:execute'],
);

final RoleInfo _adminRole = const RoleInfo(
  role: 'admin',
  scopes: <String>['read', 'op:execute', 'admin'],
);

final List<ControlItem> _fakeItems = <ControlItem>[
  const ControlItem(
    id: 'mob-stacking',
    name: 'Mob Stacking',
    category: 'Entity Population',
    enabled: true,
    description: 'Stacks nearby mobs.',
  ),
];

final List<ActionDescriptor> _fakeActions = <ActionDescriptor>[
  const ActionDescriptor(
    id: 'gc',
    name: 'Force GC',
    description: 'Triggers JVM GC.',
    destructive: false,
  ),
  const ActionDescriptor(
    id: 'purge',
    name: 'Purge Cache',
    description: 'Clears caches.',
    destructive: true,
  ),
];

void main() {
  group('OptimizationGridView role gating', () {
    testServer(
      'readOnly:true emits data-disabled="true" on toggle',
      (ServerTester tester) async {
        tester.pumpComponent(_wrap(OptimizationGridView(
          items: _fakeItems,
          total: 1,
          enabledCount: 1,
          readOnly: true,
        )));
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('data-disabled="true"'),
          isTrue,
          reason: 'readOnly:true must render toggle with data-disabled="true"',
        );
      },
    );

    testServer(
      'readOnly:false does not emit data-disabled="true" on toggle',
      (ServerTester tester) async {
        tester.pumpComponent(_wrap(OptimizationGridView(
          items: _fakeItems,
          total: 1,
          enabledCount: 1,
          readOnly: false,
        )));
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('data-disabled="true"'),
          isFalse,
          reason: 'readOnly:false must not render toggle as disabled',
        );
      },
    );
  });

  group('ActionsConsoleView role gating', () {
    testServer(
      'viewer role: destructive Execute is disabled and tooltip shows Requires admin role',
      (ServerTester tester) async {
        tester.pumpComponent(_wrap(ActionsConsoleView(
          actions: _fakeActions,
          recent: const <ActionExecution>[],
          pendingId: null,
          paramValues: const <String, Map<String, Object?>>{},
          role: _viewerRole,
          onExecute: null,
          onPendingChanged: null,
          onParamChanged: null,
        )));
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('Requires admin role'),
          isTrue,
          reason: 'viewer role must show Requires admin role tooltip for destructive action',
        );
        expect(
          res.body.contains('data-disabled="true"'),
          isTrue,
          reason: 'viewer role must disable destructive Execute button',
        );
      },
    );

    testServer(
      'operator role: destructive Execute is disabled with Requires admin role tooltip',
      (ServerTester tester) async {
        tester.pumpComponent(_wrap(ActionsConsoleView(
          actions: _fakeActions,
          recent: const <ActionExecution>[],
          pendingId: null,
          paramValues: const <String, Map<String, Object?>>{},
          role: _operatorRole,
          onExecute: null,
          onPendingChanged: null,
          onParamChanged: null,
        )));
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('Requires admin role'),
          isTrue,
          reason: 'operator role must show Requires admin role tooltip for destructive action',
        );
      },
    );

    testServer(
      'admin role: all Execute buttons enabled, no Requires admin role tooltip',
      (ServerTester tester) async {
        tester.pumpComponent(_wrap(ActionsConsoleView(
          actions: _fakeActions,
          recent: const <ActionExecution>[],
          pendingId: null,
          paramValues: const <String, Map<String, Object?>>{},
          role: _adminRole,
          onExecute: null,
          onPendingChanged: null,
          onParamChanged: null,
        )));
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('Requires admin role'),
          isFalse,
          reason: 'admin role must not show Requires admin role tooltip',
        );
        expect(
          res.body.contains('data-disabled="true"'),
          isFalse,
          reason: 'admin role must not disable Execute button',
        );
      },
    );
  });

  group('RoleBadge', () {
    testServer(
      'viewer role renders Viewer badge text',
      (ServerTester tester) async {
        tester.pumpComponent(_wrap(RoleBadge(role: _viewerRole)));
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('Viewer'),
          isTrue,
          reason: 'RoleBadge for viewer must render Viewer text',
        );
      },
    );
  });
}
