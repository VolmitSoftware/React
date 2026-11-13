library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/jaspr.dart' show Element, GlobalStateKey;
import 'package:jaspr_test/server_test.dart';

import 'package:react_web/model/role_info.dart';
import 'package:react_web/screen/logs.dart';
import 'package:react_web/service/react_exceptions.dart';
import 'package:react_web/state/connection_manager.dart';

import 'support/operate_fakes.dart';

const List<String> _lines = <String>[
  '[12:00:01 INFO]: Server started',
  '[12:00:02 WARN]: Can\'t keep up!',
  '[12:00:03 ERROR]: Could not save chunk',
  '\tat net.minecraft.Chunk.save(Chunk.java:12)',
  '[DEBUG] tick 4',
];

LogViewport _viewport() =>
    find.byType(LogViewport).evaluate().single.component as LogViewport;

TextInput _commandBox() => find
    .byType(TextInput)
    .evaluate()
    .map((Element element) => element.component as TextInput)
    .singleWhere(
      (TextInput input) => input.attributes?['aria-label'] == 'Command',
    );

void main() {
  group('log levels', () {
    test('reads the level from the line prefix', () {
      expect(logLevelOf('[12:00:01 WARN]: x'), LogLevel.warn);
      expect(logLevelOf('[12:00:01 WARNING]: x'), LogLevel.warn);
      expect(logLevelOf('[ERROR] x'), LogLevel.error);
      expect(logLevelOf('[12:00:01 SEVERE]: x'), LogLevel.error);
      expect(logLevelOf('[12:00:01 INFO]: an ERROR later'), LogLevel.info);
      expect(logLevelOf('[TRACE] x'), LogLevel.debug);
      expect(logLevelOf('plain text'), isNull);
    });

    test('a line without a level belongs to the line above', () {
      expect(
        levelledLines(_lines).map(((String, LogLevel) e) => e.$2),
        <LogLevel>[
          LogLevel.info,
          LogLevel.warn,
          LogLevel.error,
          LogLevel.error,
          LogLevel.debug,
        ],
      );
    });

    test('filters by level and text', () {
      expect(filterLogLines(_lines, level: LogLevel.error), hasLength(2));
      expect(filterLogLines(_lines, query: 'CHUNK'), hasLength(2));
      expect(
        filterLogLines(_lines, level: LogLevel.warn, query: 'chunk'),
        isEmpty,
      );
      expect(filterLogLines(_lines), hasLength(5));
    });
  });

  group('LogsScreen', () {
    testServer('without a client it asks for a live connection', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(serverScopes(null, const LogsScreen()));
      final DocumentResponse res = await tester.request('/');
      expect(res.body, contains('Logs unavailable'));
      expect(res.body, contains('Logs require a live connection.'));
    });

    testServer('a role without console:read gets No access', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        serverScopes(FakeServer(), const LogsScreen(), role: operatorRole),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.body, contains('No access'));
      expect(res.body, isNot(contains('role="log"')));
    });

    testComponents('a refused log read says No access instead of no lines', (
      ComponentTester tester,
    ) async {
      final OperateClient client = OperateClient(logs: _lines)
        ..logsError = const ReactForbidden();
      tester.pumpComponent(
        serverScopes(
          FakeServer(client: client, socket: FakeLogSocket()),
          const LogsScreen(),
        ),
      );
      await tester.pump();
      await Future<void>.delayed(const Duration(milliseconds: 80));
      await tester.pump();
      expect(find.text('No access'), findsComponents);
      expect(find.byType(LogViewport), findsNothing);
    });

    testComponents('a failed log read offers Retry, which loads the lines', (
      ComponentTester tester,
    ) async {
      final OperateClient client = OperateClient(logs: _lines)
        ..logsError = const ReactUnavailable();
      tester.pumpComponent(
        serverScopes(
          FakeServer(client: client, socket: FakeLogSocket()),
          const LogsScreen(),
        ),
      );
      await tester.pump();
      await Future<void>.delayed(const Duration(milliseconds: 80));
      await tester.pump();
      expect(find.text('Logs unavailable'), findsOneComponent);
      client.logsError = null;
      find
          .byType(Button)
          .evaluate()
          .map((Element element) => element.component as Button)
          .singleWhere((Button button) => button.label == 'Retry')
          .onPressed!();
      await Future<void>.delayed(const Duration(milliseconds: 80));
      await tester.pump();
      expect(_viewport().lines, hasLength(5));
    });

    testComponents('shows the lines and follows new ones', (
      ComponentTester tester,
    ) async {
      final FakeServer server = FakeServer(
        client: OperateClient(logs: _lines),
        socket: FakeLogSocket(),
      );
      tester.pumpComponent(serverScopes(server, const LogsScreen()));
      await tester.pump();
      expect(_viewport().lines, hasLength(5));
      server.socket!.add('[12:00:09 INFO]: Player joined');
      await Future<void>.delayed(const Duration(milliseconds: 80));
      await tester.pump();
      expect(_viewport().lines.last.$1, contains('Player joined'));
    });

    testComponents('the command box needs console:execute', (
      ComponentTester tester,
    ) async {
      tester.pumpComponent(
        serverScopes(
          FakeServer(
            client: OperateClient(logs: _lines),
            socket: FakeLogSocket(),
          ),
          const LogsScreen(),
          role: const RoleInfo(
            role: 'custom',
            scopes: <String>['read', 'console:read'],
          ),
        ),
      );
      await tester.pump();
      expect(_viewport().lines, hasLength(5));
      expect(
        find
            .byType(TextInput)
            .evaluate()
            .where(
              (Element element) =>
                  (element.component as TextInput).attributes?['aria-label'] ==
                  'Command',
            ),
        isEmpty,
      );
    });

    testComponents('the command box is off while not live', (
      ComponentTester tester,
    ) async {
      tester.pumpComponent(
        serverScopes(
          FakeServer(
            client: OperateClient(logs: _lines),
            socket: FakeLogSocket(),
          ),
          const LogsScreen(),
          state: ConnState.degraded,
        ),
      );
      await tester.pump();
      expect(_commandBox().disabled, isTrue);
    });

    testComponents('switching servers swaps the log and the command target', (
      ComponentTester tester,
    ) async {
      final FakeServer first = FakeServer(
        client: OperateClient(logs: <String>['[INFO] alpha']),
        socket: FakeLogSocket(),
      );
      final FakeServer second = FakeServer(
        client: OperateClient(logs: <String>['[INFO] beta']),
        socket: FakeLogSocket(),
      );
      final GlobalStateKey<ServerSwitcherState> key =
          GlobalStateKey<ServerSwitcherState>();
      tester.pumpComponent(
        ServerSwitcher(
          key: key,
          servers: <FakeServer>[first, second],
          child: const LogsScreen(),
        ),
      );
      await tester.pump();
      expect(_viewport().lines.single.$1, '[INFO] alpha');

      key.currentState!.show(1);
      await tester.pump();
      expect(_viewport().lines.single.$1, '[INFO] beta');

      _commandBox().onChanged!('say hello');
      await tester.pump();
      _commandBox().onSubmitted!('say hello');
      await tester.pump();
      expect(second.client.commands, <String>['say hello']);
      expect(first.client.commands, isEmpty);
    });
  });
}
