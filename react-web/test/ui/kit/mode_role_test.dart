library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr_test/server_test.dart';
import 'package:react_web/model/role_info.dart';
import 'package:react_web/state/memory_fleet_storage.dart';
import 'package:react_web/state/role_scope.dart';
import 'package:react_web/theme/reactor_stylesheet.dart';
import 'package:react_web/ui/kit/mode.dart';
import 'package:react_web/ui/kit/role_gate.dart';

const RoleInfo _viewer = RoleInfo(role: 'viewer', scopes: <String>['read']);
const RoleInfo _operator = RoleInfo(
  role: 'operator',
  scopes: <String>['read', 'op:execute'],
);
const RoleInfo _admin = RoleInfo(
  role: 'admin',
  scopes: <String>[
    'read',
    'op:execute',
    'admin',
    'console:read',
    'console:execute',
  ],
);

void main() {
  group('mode', () {
    test('anything but advanced is Simple', () {
      expect(parseReactorMode(null), ReactorMode.simple);
      expect(parseReactorMode('expert'), ReactorMode.simple);
      expect(parseReactorMode('advanced'), ReactorMode.advanced);
    });

    test('is kept under reactor.mode', () {
      final InMemoryFleetStorage storage = InMemoryFleetStorage();
      expect(loadReactorMode(storage), ReactorMode.simple);
      persistReactorMode(storage, ReactorMode.advanced);
      expect(storage.read(reactorModeStorageKey), 'advanced');
      expect(reactorModeStorageKey, 'reactor.mode');
      expect(loadReactorMode(storage), ReactorMode.advanced);
      expect(loadReactorMode(null), ReactorMode.simple);
    });

    testServer('ModeGate shows its child in Advanced only', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        ArcaneThemeProvider(
          stylesheet: const ReactorStylesheet(),
          child: ModeScope(
            mode: ReactorMode.simple,
            onChanged: (ReactorMode _) {},
            child: const ModeGate(simple: Text('plain'), child: Text('expert')),
          ),
        ),
      );
      final DocumentResponse response = await tester.request('/');
      expect(response.body, contains('plain'));
      expect(response.body, isNot(contains('expert')));
    });
  });

  group('roles', () {
    test('allow by scope and while unknown', () {
      expect(roleAllows(null, RoleNeed.admin), isTrue);
      expect(roleAllows(_viewer, RoleNeed.operator), isFalse);
      expect(roleAllows(_operator, RoleNeed.operator), isTrue);
      expect(roleAllows(_operator, RoleNeed.admin), isFalse);
      expect(roleAllows(_operator, RoleNeed.console), isFalse);
      expect(roleAllows(_admin, RoleNeed.console), isTrue);
    });

    testServer('RoleGate shows denied for a role without the scope', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        ArcaneThemeProvider(
          stylesheet: const ReactorStylesheet(),
          child: const RoleScope(
            role: _viewer,
            child: RoleGate(
              need: RoleNeed.operator,
              denied: Text('read only'),
              child: Text('run it'),
            ),
          ),
        ),
      );
      final DocumentResponse response = await tester.request('/');
      expect(response.body, contains('read only'));
      expect(response.body, isNot(contains('run it')));
    });
  });
}
