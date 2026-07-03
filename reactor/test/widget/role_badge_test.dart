library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr_test/server_test.dart';
import 'package:test/test.dart';

import 'package:reactor/model/role_info.dart';
import 'package:reactor/widget/role_badge.dart';

const ShadcnStylesheet _sheet = ShadcnStylesheet(theme: ShadcnTheme.midnight);

Widget _wrap(Widget child) =>
    ArcaneThemeProvider(stylesheet: _sheet, child: child);

void main() {
  group('RoleBadge', () {
    testServer(
      'renders Admin for admin role',
      (ServerTester tester) async {
        tester.pumpComponent(
          _wrap(
            RoleBadge(
              role: const RoleInfo(
                role: 'admin',
                scopes: <String>['read', 'op:execute', 'admin'],
              ),
            ),
          ),
        );
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('Admin'),
          isTrue,
          reason: 'admin role must render Admin label',
        );
      },
    );

    testServer(
      'renders Operator for operator role',
      (ServerTester tester) async {
        tester.pumpComponent(
          _wrap(
            RoleBadge(
              role: const RoleInfo(
                role: 'operator',
                scopes: <String>['read', 'op:execute'],
              ),
            ),
          ),
        );
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('Operator'),
          isTrue,
          reason: 'operator role must render Operator label',
        );
      },
    );

    testServer(
      'renders Viewer for viewer role',
      (ServerTester tester) async {
        tester.pumpComponent(
          _wrap(
            RoleBadge(
              role: const RoleInfo(
                role: 'viewer',
                scopes: <String>['read'],
              ),
            ),
          ),
        );
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('Viewer'),
          isTrue,
          reason: 'viewer role must render Viewer label',
        );
      },
    );

    testServer(
      'renders nothing when role is null',
      (ServerTester tester) async {
        tester.pumpComponent(
          _wrap(RoleBadge(role: null)),
        );
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('Admin') ||
              res.body.contains('Operator') ||
              res.body.contains('Viewer'),
          isFalse,
          reason: 'null role must not render any role label',
        );
      },
    );
  });
}
