import 'package:test/test.dart';

import 'package:react_web/service/react_exceptions.dart';

void main() {
  group('ReactForbidden', () {
    test('is an Exception', () {
      expect(const ReactForbidden(), isA<Exception>());
    });

    test('default message is non-empty', () {
      expect(const ReactForbidden().message, isNotEmpty);
    });

    test('toString contains class name and message', () {
      final ReactForbidden e = const ReactForbidden();
      expect(e.toString(), contains('ReactForbidden'));
      expect(e.toString(), contains(e.message));
    });

    test('custom message is preserved', () {
      const ReactForbidden e = ReactForbidden('custom forbidden');
      expect(e.message, equals('custom forbidden'));
      expect(e.toString(), contains('custom forbidden'));
    });
  });

  group('ReactNotFound', () {
    test('is an Exception', () {
      expect(const ReactNotFound(), isA<Exception>());
    });

    test('default message is non-empty', () {
      expect(const ReactNotFound().message, isNotEmpty);
    });

    test('toString contains class name and message', () {
      final ReactNotFound e = const ReactNotFound();
      expect(e.toString(), contains('ReactNotFound'));
      expect(e.toString(), contains(e.message));
    });

    test('custom message is preserved', () {
      const ReactNotFound e = ReactNotFound('custom not found');
      expect(e.message, equals('custom not found'));
      expect(e.toString(), contains('custom not found'));
    });
  });

  group('ReactConflict', () {
    test('is an Exception', () {
      expect(const ReactConflict(), isA<Exception>());
    });

    test('default message is non-empty', () {
      expect(const ReactConflict().message, isNotEmpty);
    });

    test('toString contains class name and message', () {
      final ReactConflict e = const ReactConflict();
      expect(e.toString(), contains('ReactConflict'));
      expect(e.toString(), contains(e.message));
    });

    test('custom message is preserved', () {
      const ReactConflict e = ReactConflict('custom conflict');
      expect(e.message, equals('custom conflict'));
      expect(e.toString(), contains('custom conflict'));
    });
  });
}
