library;

import 'dart:async';

import 'package:test/test.dart';

import 'package:react_web/service/react_client.dart';
import 'package:react_web/service/react_log_socket.dart';
import 'package:react_web/state/log_controller.dart';

class _FakeLogClient implements ILogClient {
  List<String> result;
  bool shouldThrow;

  _FakeLogClient({this.result = const <String>[], this.shouldThrow = false});

  @override
  Future<List<String>> logs({int limit = 200}) async {
    if (shouldThrow) throw Exception('forced error');
    return result;
  }
}

class _FakeLogSocket implements ILogSocket {
  final StreamController<String> _ctrl = StreamController<String>.broadcast();
  bool closed = false;

  @override
  Stream<String> get lines => _ctrl.stream;

  void add(String line) => _ctrl.add(line);

  @override
  Future<void> close() async {
    closed = true;
    await _ctrl.close();
  }
}

void main() {
  group('LogController.load', () {
    test(
      'seeds buffer from client.logs and visible matches seeded list',
      () async {
        final List<String> seed = <String>['[INFO] a', '[WARN] b', '[ERROR] c'];
        final _FakeLogClient client = _FakeLogClient(result: seed);
        final LogController ctrl = LogController(client);

        await ctrl.load();

        expect(ctrl.visible, equals(seed));
      },
    );

    test('sets loading false after load completes', () async {
      final _FakeLogClient client = _FakeLogClient(result: <String>['line1']);
      final LogController ctrl = LogController(client);

      await ctrl.load();

      expect(ctrl.loading, isFalse);
    });

    test('notifies onChange at least once on success', () async {
      final _FakeLogClient client = _FakeLogClient(
        result: <String>['[INFO] hello'],
      );
      int count = 0;
      final LogController ctrl = LogController(client, onChange: () => count++);

      await ctrl.load();

      expect(count, greaterThanOrEqualTo(1));
    });

    test('calls onError when client throws', () async {
      final _FakeLogClient client = _FakeLogClient(shouldThrow: true);
      Object? caught;
      final LogController ctrl = LogController(
        client,
        onError: (Object e) => caught = e,
      );

      await ctrl.load();

      expect(caught, isNotNull);
      expect(ctrl.visible, isEmpty);
    });

    test('replaces buffer on second load call', () async {
      final _FakeLogClient client = _FakeLogClient(
        result: <String>['first load'],
      );
      final LogController ctrl = LogController(client);
      await ctrl.load();
      client.result = <String>['second load'];

      await ctrl.load();

      expect(ctrl.visible, equals(<String>['second load']));
    });
  });

  group('LogController.start + socket', () {
    test('start() then add line -> appears in visible', () async {
      final _FakeLogClient client = _FakeLogClient();
      final _FakeLogSocket socket = _FakeLogSocket();
      final LogController ctrl = LogController(client, socket: socket);
      await ctrl.load();
      ctrl.start();

      socket.add('[INFO] live line');
      await Future<void>.delayed(Duration.zero);

      expect(ctrl.visible, contains('[INFO] live line'));
    });

    test('paused: line added during pause is NOT appended', () async {
      final _FakeLogClient client = _FakeLogClient(
        result: <String>['[INFO] seed'],
      );
      final _FakeLogSocket socket = _FakeLogSocket();
      final LogController ctrl = LogController(client, socket: socket);
      await ctrl.load();
      ctrl.start();
      ctrl.setPaused(true);

      socket.add('[WARN] dropped');
      await Future<void>.delayed(Duration.zero);

      expect(ctrl.visible, isNot(contains('[WARN] dropped')));
      expect(ctrl.visible.length, equals(1));
    });

    test('resumed: line after resume is appended', () async {
      final _FakeLogClient client = _FakeLogClient(
        result: <String>['[INFO] seed'],
      );
      final _FakeLogSocket socket = _FakeLogSocket();
      final LogController ctrl = LogController(client, socket: socket);
      await ctrl.load();
      ctrl.start();
      ctrl.setPaused(true);

      socket.add('[WARN] dropped');
      await Future<void>.delayed(Duration.zero);
      ctrl.setPaused(false);
      socket.add('[INFO] after resume');
      await Future<void>.delayed(Duration.zero);

      expect(ctrl.visible, contains('[INFO] after resume'));
      expect(ctrl.visible, isNot(contains('[WARN] dropped')));
    });
  });

  group('LogController.clear', () {
    test('empties visible buffer', () async {
      final _FakeLogClient client = _FakeLogClient(
        result: <String>['[INFO] a', '[ERROR] b'],
      );
      final LogController ctrl = LogController(client);
      await ctrl.load();

      ctrl.clear();

      expect(ctrl.visible, isEmpty);
    });

    test('calls onChange after clear', () async {
      final _FakeLogClient client = _FakeLogClient(result: <String>['x']);
      int count = 0;
      final LogController ctrl = LogController(client, onChange: () => count++);
      await ctrl.load();
      count = 0;

      ctrl.clear();

      expect(count, greaterThanOrEqualTo(1));
    });
  });

  group('LogController.setLevelFilter', () {
    test('ERROR filter returns only ERROR lines', () async {
      final _FakeLogClient client = _FakeLogClient(
        result: <String>['[INFO] a', '[ERROR] b', '[WARN] c'],
      );
      final LogController ctrl = LogController(client);
      await ctrl.load();

      ctrl.setLevelFilter('ERROR');

      expect(ctrl.visible, equals(<String>['[ERROR] b']));
    });

    test('ALL filter restores all lines', () async {
      final _FakeLogClient client = _FakeLogClient(
        result: <String>['[INFO] a', '[ERROR] b', '[WARN] c'],
      );
      final LogController ctrl = LogController(client);
      await ctrl.load();
      ctrl.setLevelFilter('ERROR');

      ctrl.setLevelFilter('ALL');

      expect(ctrl.visible.length, equals(3));
    });

    test('INFO filter returns only INFO lines', () async {
      final _FakeLogClient client = _FakeLogClient(
        result: <String>['[INFO] a', '[ERROR] b', '[INFO] c'],
      );
      final LogController ctrl = LogController(client);
      await ctrl.load();

      ctrl.setLevelFilter('INFO');

      expect(ctrl.visible, equals(<String>['[INFO] a', '[INFO] c']));
    });

    test('WARN filter returns only WARN lines', () async {
      final _FakeLogClient client = _FakeLogClient(
        result: <String>['[INFO] a', '[WARN] b', '[ERROR] c'],
      );
      final LogController ctrl = LogController(client);
      await ctrl.load();

      ctrl.setLevelFilter('WARN');

      expect(ctrl.visible, equals(<String>['[WARN] b']));
    });
  });

  group('LogController buffer cap', () {
    test('caps at maxLines=1000 when pushing 1100 lines', () async {
      final List<String> big = List<String>.generate(
        1100,
        (int i) => '[INFO] line $i',
      );
      final _FakeLogClient client = _FakeLogClient(result: big);
      final LogController ctrl = LogController(client);

      await ctrl.load();

      expect(ctrl.visible.length, equals(1000));
    });

    test('oldest lines dropped when buffer overflows', () async {
      final List<String> big = List<String>.generate(
        1100,
        (int i) => '[INFO] line $i',
      );
      final _FakeLogClient client = _FakeLogClient(result: big);
      final LogController ctrl = LogController(client);
      await ctrl.load();

      expect(ctrl.visible.first, equals('[INFO] line 100'));
      expect(ctrl.visible.last, equals('[INFO] line 1099'));
    });
  });

  group('LogController.dispose', () {
    test('cancels socket subscription and closes socket', () async {
      final _FakeLogClient client = _FakeLogClient();
      final _FakeLogSocket socket = _FakeLogSocket();
      final LogController ctrl = LogController(client, socket: socket);
      ctrl.start();

      ctrl.dispose();

      expect(socket.closed, isTrue);
    });
  });
}
