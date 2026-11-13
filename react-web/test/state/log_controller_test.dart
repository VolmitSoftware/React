library;

import 'dart:async';

import 'package:test/test.dart';

import 'package:react_web/model/log_line.dart';
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
  final StreamController<LogBatch> _ctrl =
      StreamController<LogBatch>.broadcast();
  bool closed = false;

  @override
  Stream<LogBatch> get batches => _ctrl.stream;

  void add(String line) => _ctrl.add(LogBatch(<LogLine>[LogLine(line)]));

  @override
  Future<void> close() async {
    closed = true;
    await _ctrl.close();
  }
}

void main() {
  test('batches a burst while retaining bounded ordered lines', () async {
    final _FakeLogSocket socket = _FakeLogSocket();
    int notifications = 0;
    final LogController controller = LogController(
      _FakeLogClient(),
      socket: socket,
    )..addListener(() => notifications++);
    controller.start();
    for (int index = 0; index < 1200; index++) {
      socket.add('[INFO] $index');
    }
    await Future<void>.delayed(Duration.zero);
    expect(controller.lines.length, LogController.maxLines);
    expect(controller.lines.first, '[INFO] 200');
    expect(controller.lines.last, '[INFO] 1199');
    expect(notifications, 0);
    await Future<void>.delayed(const Duration(milliseconds: 70));
    expect(notifications, 1);
    final List<String> cached = controller.lines;
    expect(identical(controller.lines, cached), isTrue);
    controller.dispose();
  });

  test('disposing cancels scheduled rendering', () async {
    final _FakeLogSocket socket = _FakeLogSocket();
    int notifications = 0;
    final LogController controller = LogController(
      _FakeLogClient(),
      socket: socket,
    )..addListener(() => notifications++);
    controller.start();
    socket.add('[INFO] last');
    await Future<void>.delayed(Duration.zero);
    controller.dispose();
    await Future<void>.delayed(const Duration(milliseconds: 70));
    expect(notifications, 0);
  });

  group('LogController.load', () {
    test(
      'seeds buffer from client.logs and visible matches seeded list',
      () async {
        final List<String> seed = <String>['[INFO] a', '[WARN] b', '[ERROR] c'];
        final _FakeLogClient client = _FakeLogClient(result: seed);
        final LogController ctrl = LogController(client);

        await ctrl.load();

        expect(ctrl.lines, equals(seed));
      },
    );

    test('sets loading false after load completes', () async {
      final _FakeLogClient client = _FakeLogClient(result: <String>['line1']);
      final LogController ctrl = LogController(client);

      await ctrl.load();

      expect(ctrl.loading, isFalse);
    });

    test('notifies listeners at least once on success', () async {
      final _FakeLogClient client = _FakeLogClient(
        result: <String>['[INFO] hello'],
      );
      int count = 0;
      final LogController ctrl = LogController(client)
        ..addListener(() => count++);

      await ctrl.load();

      expect(count, greaterThanOrEqualTo(1));
    });

    test('keeps the error when the first load fails', () async {
      final _FakeLogClient client = _FakeLogClient(shouldThrow: true);
      final LogController ctrl = LogController(client);

      await ctrl.load();

      expect(ctrl.error, isNotNull);
      expect(ctrl.lines, isEmpty);
    });

    test('clears the error once a load succeeds', () async {
      final _FakeLogClient client = _FakeLogClient(shouldThrow: true);
      final LogController ctrl = LogController(client);
      await ctrl.load();
      expect(ctrl.error, isNotNull);

      client
        ..shouldThrow = false
        ..result = <String>['[INFO] back'];
      await ctrl.load();

      expect(ctrl.error, isNull);
      expect(ctrl.lines, <String>['[INFO] back']);
    });

    test('replaces buffer on second load call', () async {
      final _FakeLogClient client = _FakeLogClient(
        result: <String>['first load'],
      );
      final LogController ctrl = LogController(client);
      await ctrl.load();
      client.result = <String>['second load'];

      await ctrl.load();

      expect(ctrl.lines, equals(<String>['second load']));
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

      expect(ctrl.lines, contains('[INFO] live line'));
    });

    test('paused: line added during pause is held, not shown', () async {
      final _FakeLogClient client = _FakeLogClient(
        result: <String>['[INFO] seed'],
      );
      final _FakeLogSocket socket = _FakeLogSocket();
      final LogController ctrl = LogController(client, socket: socket);
      await ctrl.load();
      ctrl.start();
      ctrl.setPaused(true);

      socket.add('[WARN] held');
      await Future<void>.delayed(Duration.zero);

      expect(ctrl.lines, isNot(contains('[WARN] held')));
      expect(ctrl.lines.length, equals(1));
      expect(ctrl.heldCount, equals(1));
    });

    test('resumed: held lines show in order before newer ones', () async {
      final _FakeLogClient client = _FakeLogClient(
        result: <String>['[INFO] seed'],
      );
      final _FakeLogSocket socket = _FakeLogSocket();
      final LogController ctrl = LogController(client, socket: socket);
      await ctrl.load();
      ctrl.start();
      ctrl.setPaused(true);

      socket.add('[WARN] held');
      await Future<void>.delayed(Duration.zero);
      ctrl.setPaused(false);
      socket.add('[INFO] after resume');
      await Future<void>.delayed(Duration.zero);

      expect(ctrl.lines, <String>[
        '[INFO] seed',
        '[WARN] held',
        '[INFO] after resume',
      ]);
      expect(ctrl.heldCount, equals(0));
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

      expect(ctrl.lines, isEmpty);
    });

    test('notifies listeners after clear', () async {
      final _FakeLogClient client = _FakeLogClient(result: <String>['x']);
      int count = 0;
      final LogController ctrl = LogController(client)
        ..addListener(() => count++);
      await ctrl.load();
      count = 0;

      ctrl.clear();

      expect(count, greaterThanOrEqualTo(1));
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

      expect(ctrl.lines.length, equals(1000));
    });

    test('oldest lines dropped when buffer overflows', () async {
      final List<String> big = List<String>.generate(
        1100,
        (int i) => '[INFO] line $i',
      );
      final _FakeLogClient client = _FakeLogClient(result: big);
      final LogController ctrl = LogController(client);
      await ctrl.load();

      expect(ctrl.lines.first, equals('[INFO] line 100'));
      expect(ctrl.lines.last, equals('[INFO] line 1099'));
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
