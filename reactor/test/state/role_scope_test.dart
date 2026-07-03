library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr_shadcn/arcane_jaspr_shadcn.dart';
import 'package:jaspr/jaspr.dart' show Component;
import 'package:jaspr_test/server_test.dart';
import 'package:test/test.dart';

import 'package:reactor/model/role_info.dart';
import 'package:reactor/service/react_client.dart';
import 'package:reactor/service/react_exceptions.dart';
import 'package:reactor/state/role_scope.dart';

class _AdminClient implements IRoleClient {
  @override
  Future<RoleInfo> whoami() async => const RoleInfo(
        role: 'admin',
        scopes: <String>['read', 'op:execute', 'admin'],
      );
}

class _ForbiddenClient implements IRoleClient {
  @override
  Future<RoleInfo> whoami() async =>
      throw const ReactForbidden('not allowed');
}

class _Probe extends StatelessWidget {
  const _Probe();

  @override
  Widget build(BuildContext context) {
    final String? role = RoleScope.of(context)?.role?.role;
    if (role == null) return Component.fragment(const <Widget>[]);
    return Component.text(role);
  }
}

const ShadcnStylesheet _sheet = ShadcnStylesheet(theme: ShadcnTheme.midnight);

Widget _wrap(Widget child) =>
    ArcaneThemeProvider(stylesheet: _sheet, child: child);

void main() {
  group('RoleScope', () {
    test('updateShouldNotify returns false when role string is same', () {
      const RoleInfo r = RoleInfo(role: 'admin', scopes: <String>[]);
      final RoleScope s1 = RoleScope(
        role: r,
        child: Component.fragment(const <Widget>[]),
      );
      final RoleScope s2 = RoleScope(
        role: const RoleInfo(role: 'admin', scopes: <String>[]),
        child: Component.fragment(const <Widget>[]),
      );
      expect(s1.updateShouldNotify(s2), isFalse);
    });

    test('updateShouldNotify returns true when role string changes', () {
      final RoleScope s1 = RoleScope(
        role: const RoleInfo(role: 'admin', scopes: <String>[]),
        child: Component.fragment(const <Widget>[]),
      );
      final RoleScope s2 = RoleScope(
        role: const RoleInfo(role: 'viewer', scopes: <String>[]),
        child: Component.fragment(const <Widget>[]),
      );
      expect(s1.updateShouldNotify(s2), isTrue);
    });

    test('updateShouldNotify returns true when going from null to non-null', () {
      final RoleScope s1 = RoleScope(
        role: null,
        child: Component.fragment(const <Widget>[]),
      );
      final RoleScope s2 = RoleScope(
        role: const RoleInfo(role: 'admin', scopes: <String>[]),
        child: Component.fragment(const <Widget>[]),
      );
      expect(s1.updateShouldNotify(s2), isTrue);
    });
  });

  group('RoleObserver', () {
    testServer(
      'probe renders admin role after whoami resolves',
      (ServerTester tester) async {
        tester.pumpComponent(
          _wrap(
            RoleObserver(
              client: _AdminClient(),
              child: const _Probe(),
            ),
          ),
        );
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('admin'),
          isTrue,
          reason: 'probe must render the role string returned by whoami()',
        );
      },
    );

    testServer(
      'probe renders nothing when client throws ReactForbidden',
      (ServerTester tester) async {
        tester.pumpComponent(
          _wrap(
            RoleObserver(
              client: _ForbiddenClient(),
              child: const _Probe(),
            ),
          ),
        );
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('admin') ||
              res.body.contains('operator') ||
              res.body.contains('viewer'),
          isFalse,
          reason:
              'probe must not render any role text when whoami() throws a forbidden error',
        );
      },
    );

    testServer(
      'probe renders nothing when client is null',
      (ServerTester tester) async {
        tester.pumpComponent(
          _wrap(
            RoleObserver(
              client: null,
              child: const _Probe(),
            ),
          ),
        );
        final DocumentResponse res = await tester.request('/');
        expect(res.statusCode, equals(200));
        expect(
          res.body.contains('admin') ||
              res.body.contains('operator') ||
              res.body.contains('viewer'),
          isFalse,
          reason: 'probe must not render any role text when client is null',
        );
      },
    );
  });
}
