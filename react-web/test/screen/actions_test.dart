library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/jaspr.dart' show GlobalStateKey;
import 'package:jaspr_test/server_test.dart';

import 'package:react_web/model/action_descriptor.dart';
import 'package:react_web/model/knob.dart';
import 'package:react_web/screen/actions.dart';
import 'package:react_web/state/actions_controller.dart';
import 'package:react_web/state/memory_fleet_storage.dart';
import 'package:react_web/ui/kit/badge.dart';

import 'support/operate_fakes.dart';

const ActionDescriptor _gc = ActionDescriptor(
  id: 'collect-garbage',
  name: 'Collect Garbage',
  description: 'Queues a controlled garbage collection request.',
  destructive: false,
);

const ActionDescriptor _purge = ActionDescriptor(
  id: 'purge-entities',
  name: 'Purge Entities',
  description: 'Removes eligible entities. Players are never touched.',
  destructive: true,
);

const ActionDescriptor _prewarm = ActionDescriptor(
  id: 'prewarm',
  name: 'Prewarm Chunks',
  description: 'Loads chunks ahead of a join wave.',
  destructive: false,
  params: <ActionParam>[
    ActionParam(
      key: 'radius',
      label: 'Radius',
      type: KnobType.intType,
      required: true,
      defaultValue: 8,
    ),
  ],
);

ActionsView _view() =>
    find.byType(ActionsView).evaluate().single.component as ActionsView;

ActionParamsDialog _dialog() =>
    find.byType(ActionParamsDialog).evaluate().single.component
        as ActionParamsDialog;

Widget _wrap(Widget child) =>
    ArcaneThemeProvider(stylesheet: testSheet, child: child);

void main() {
  group('helpers', () {
    test('firstSentence keeps the first sentence only', () {
      expect(firstSentence(_purge.description), 'Removes eligible entities.');
      expect(firstSentence(_gc.description), _gc.description);
      expect(firstSentence(''), '');
    });

    test('whatItDoes drops the plugin config preamble', () {
      expect(
        whatItDoes(
          'Configuration for Collect Garbage action. Requests JVM garbage '
          'collection and reports immediate heap reclaimed.',
        ),
        'Requests JVM garbage collection and reports immediate heap '
        'reclaimed.',
      );
      expect(
        whatItDoes('Configuration for Unknown action.'),
        'Configuration for Unknown action.',
        reason: 'nothing else to say',
      );
      expect(
        whatItDoes('Removes eligible entities.'),
        'Removes eligible entities.',
      );
      expect(whatItDoes(''), '');
    });

    test('ActionUsage leads with the most used, ties in server order', () {
      final ActionUsage usage = ActionUsage(InMemoryFleetStorage());
      usage.count(_purge.id);
      usage.count(_purge.id);
      usage.count(_prewarm.id);
      expect(
        ActionUsage.order(<ActionDescriptor>[
          _gc,
          _prewarm,
          _purge,
        ], usage.read()).map((ActionDescriptor a) => a.id),
        <String>[_purge.id, _prewarm.id, _gc.id],
      );
      expect(ActionUsage(null).read(), isEmpty);
    });

    test('runState names every ticket state', () {
      expect(runState('queued'), ('Queued', BadgeTone.neutral));
      expect(runState('RUNNING'), ('Running', BadgeTone.info));
      expect(runState('done'), ('Done', BadgeTone.ok));
      expect(runState('failed'), ('Failed', BadgeTone.error));
    });
  });

  group('ActionsView', () {
    testServer('lists actions with their effect and a Run button', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(
        _wrap(
          ActionsView(
            actions: const <ActionDescriptor>[_gc, _purge],
            recent: const <ActionExecution>[],
            onRun: (ActionDescriptor _) {},
          ),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.body, contains('Collect Garbage'));
      expect(res.body, contains('Removes eligible entities.'));
      expect(res.body, contains('Destructive'));
      expect(res.body, contains('aria-label="Run Purge Entities"'));
      expect(res.body, contains('Recent runs'));
      expect(res.body, contains('No runs yet'));
    });

    testServer('recent runs show their state', (ServerTester tester) async {
      tester.pumpComponent(
        _wrap(
          ActionsView(
            actions: const <ActionDescriptor>[_gc],
            recent: <ActionExecution>[
              ActionExecution(
                actionId: _gc.id,
                ticketId: 't-1',
                status: 'queued',
                at: DateTime.now(),
              ),
            ],
            onRun: null,
          ),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.body, contains('Queued'));
      expect(res.body, contains('Collect Garbage'));
    });

    testServer('no actions says so', (ServerTester tester) async {
      tester.pumpComponent(
        _wrap(
          const ActionsView(
            actions: <ActionDescriptor>[],
            recent: <ActionExecution>[],
            onRun: null,
          ),
        ),
      );
      final DocumentResponse res = await tester.request('/');
      expect(res.body, contains('No actions available'));
    });
  });

  group('ActionsScreen', () {
    testServer('without a client it asks for a live connection', (
      ServerTester tester,
    ) async {
      tester.pumpComponent(serverScopes(null, const ActionsScreen()));
      final DocumentResponse res = await tester.request('/');
      expect(res.body, contains('Actions unavailable'));
      expect(res.body, contains('Actions require a live connection.'));
    });

    testComponents('runs an action without settings at once', (
      ComponentTester tester,
    ) async {
      final FakeServer server = FakeServer(
        client: OperateClient(actions: <ActionDescriptor>[_gc]),
      );
      tester.pumpComponent(serverScopes(server, const ActionsScreen()));
      await tester.pump();
      _view().onRun!(_gc);
      await tester.pump();
      expect(server.client.runs.single.$1, 'collect-garbage');
      expect(server.client.runs.single.$3, isFalse);
      expect(_view().recent.single.ticketId, endsWith('ticket-1'));
    });

    testComponents('an action with settings opens them first', (
      ComponentTester tester,
    ) async {
      final FakeServer server = FakeServer(
        client: OperateClient(actions: <ActionDescriptor>[_prewarm]),
      );
      tester.pumpComponent(serverScopes(server, const ActionsScreen()));
      await tester.pump();
      _view().onRun!(_prewarm);
      await tester.pump();
      expect(_dialog().action, _prewarm);
      _dialog().onChanged('radius', 12);
      await tester.pump();
      _dialog().onRun();
      await tester.pump();
      expect(server.client.runs.single.$2, <String, Object?>{'radius': 12});
      expect(_dialog().action, isNull);
    });

    testComponents('a destructive action is not run without a yes', (
      ComponentTester tester,
    ) async {
      final FakeServer server = FakeServer(
        client: OperateClient(actions: <ActionDescriptor>[_purge]),
      );
      tester.pumpComponent(serverScopes(server, const ActionsScreen()));
      await tester.pump();
      _view().onRun!(_purge);
      await tester.pump();
      expect(server.client.runs, isEmpty);
    });

    testComponents('a destructive action runs after a yes, confirmed', (
      ComponentTester tester,
    ) async {
      final FakeServer server = FakeServer(
        client: OperateClient(actions: <ActionDescriptor>[_purge]),
      );
      tester.pumpComponent(
        withConfirmHost(serverScopes(server, const ActionsScreen())),
      );
      await tester.pump();
      _view().onRun!(_purge);
      await tester.pump();
      expect(find.text('Run Purge Entities on Server?'), findsOneComponent);
      await answerConfirm(tester, yes: true);
      expect(server.client.runs.single.$1, 'purge-entities');
      expect(server.client.runs.single.$3, isTrue);
      await unmount(tester);
    });

    testComponents('a destructive action with settings asks once, in its '
        'settings dialog', (ComponentTester tester) async {
      const ActionDescriptor wipe = ActionDescriptor(
        id: 'wipe-chunks',
        name: 'Wipe Chunks',
        description:
            'Configuration for Wipe Chunks action. Unloads hot chunks.',
        destructive: true,
        params: <ActionParam>[
          ActionParam(
            key: 'radius',
            label: 'Radius',
            type: KnobType.intType,
            required: true,
            defaultValue: 4,
          ),
        ],
      );
      final FakeServer server = FakeServer(
        client: OperateClient(actions: <ActionDescriptor>[wipe]),
      );
      tester.pumpComponent(
        withConfirmHost(serverScopes(server, const ActionsScreen())),
      );
      await tester.pump();
      _view().onRun!(wipe);
      await tester.pump();
      expect(find.text('Run Wipe Chunks on Server?'), findsOneComponent);
      expect(
        find.descendant(
          of: find.byType(ActionParamsDialog),
          matching: find.text('Unloads hot chunks.'),
        ),
        findsOneComponent,
      );
      _dialog().onRun();
      await tester.pump();
      await tester.pump();
      expect(server.client.runs.single.$1, 'wipe-chunks');
      await unmount(tester);
    });

    testComponents('a viewer cannot run anything', (
      ComponentTester tester,
    ) async {
      tester.pumpComponent(
        serverScopes(
          FakeServer(client: OperateClient(actions: <ActionDescriptor>[_gc])),
          const ActionsScreen(),
          role: viewerRole,
        ),
      );
      await tester.pump();
      expect(_view().actions, hasLength(1));
      expect(_view().onRun, isNull);
    });

    testComponents('switching servers reloads and runs on the new server', (
      ComponentTester tester,
    ) async {
      final FakeServer first = FakeServer(
        client: OperateClient(actions: <ActionDescriptor>[_gc]),
      );
      final FakeServer second = FakeServer(
        client: OperateClient(actions: <ActionDescriptor>[_prewarm, _gc]),
      );
      final GlobalStateKey<ServerSwitcherState> key =
          GlobalStateKey<ServerSwitcherState>();
      tester.pumpComponent(
        ServerSwitcher(
          key: key,
          servers: <FakeServer>[first, second],
          child: const ActionsScreen(),
        ),
      );
      await tester.pump();
      _view().onRun!(_gc);
      await tester.pump();
      expect(_view().recent, hasLength(1));

      key.currentState!.show(1);
      await tester.pump();
      expect(_view().actions, hasLength(2));
      expect(_view().recent, isEmpty);

      _view().onRun!(_gc);
      await tester.pump();
      expect(second.client.runs.single.$1, 'collect-garbage');
      expect(first.client.runs, hasLength(1));
    });
  });
}
