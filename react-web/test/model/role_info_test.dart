library;

import 'package:test/test.dart';

import 'package:react_web/model/role_info.dart';

void main() {
  group('RoleInfo.fromJson', () {
    test('parses operator role with read and execute scopes', () {
      final RoleInfo info = RoleInfo.fromJson(<String, dynamic>{
        'role': 'operator',
        'scopes': <dynamic>['read', 'op:execute'],
      });
      expect(info.role, equals('operator'));
      expect(info.canRead, isTrue);
      expect(info.canExecute, isTrue);
      expect(info.isAdmin, isFalse);
      expect(info.isViewer, isFalse);
    });

    test('parses admin scopes -> isAdmin true', () {
      final RoleInfo info = RoleInfo.fromJson(<String, dynamic>{
        'role': 'admin',
        'scopes': <dynamic>['read', 'op:execute', 'console:execute', 'admin'],
      });
      expect(info.isAdmin, isTrue);
      expect(info.canExecuteConsole, isTrue);
    });

    test(
      'parses viewer with read scope only -> canExecute false isViewer true',
      () {
        final RoleInfo info = RoleInfo.fromJson(<String, dynamic>{
          'role': 'viewer',
          'scopes': <dynamic>['read'],
        });
        expect(info.canExecute, isFalse);
        expect(info.canExecuteConsole, isFalse);
        expect(info.isViewer, isTrue);
      },
    );

    test('missing scopes key yields empty list', () {
      final RoleInfo info = RoleInfo.fromJson(<String, dynamic>{
        'role': 'viewer',
      });
      expect(info.scopes, isEmpty);
      expect(info.canRead, isFalse);
    });
  });

  group('readOnlyFor', () {
    test('returns true for viewer (no execute scope)', () {
      final RoleInfo viewer = RoleInfo.fromJson(<String, dynamic>{
        'role': 'viewer',
        'scopes': <dynamic>['read'],
      });
      expect(readOnlyFor(viewer), isTrue);
    });

    test('returns false for operator with execute scope', () {
      final RoleInfo operator = RoleInfo.fromJson(<String, dynamic>{
        'role': 'operator',
        'scopes': <dynamic>['read', 'op:execute'],
      });
      expect(readOnlyFor(operator), isFalse);
    });

    test('returns false for null (unknown/loading)', () {
      expect(readOnlyFor(null), isFalse);
    });
  });

  group('adminGatedDisabled', () {
    test('returns true for operator without admin scope', () {
      final RoleInfo operator = RoleInfo.fromJson(<String, dynamic>{
        'role': 'operator',
        'scopes': <dynamic>['read', 'op:execute'],
      });
      expect(adminGatedDisabled(operator), isTrue);
    });

    test('returns false for admin with admin scope', () {
      final RoleInfo admin = RoleInfo.fromJson(<String, dynamic>{
        'role': 'admin',
        'scopes': <dynamic>['read', 'op:execute', 'admin'],
      });
      expect(adminGatedDisabled(admin), isFalse);
    });

    test('returns false for null (unknown/loading)', () {
      expect(adminGatedDisabled(null), isFalse);
    });
  });
}
