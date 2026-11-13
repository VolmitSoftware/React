library;

import 'dart:async';

import 'package:test/test.dart';

import 'package:react_web/model/action_descriptor.dart';
import 'package:react_web/model/config_tree.dart';
import 'package:react_web/model/control_item.dart';
import 'package:react_web/model/knob.dart';
import 'package:react_web/model/server_revisions.dart';
import 'package:react_web/model/server_snapshot.dart';
import 'package:react_web/model/world_settings.dart';
import 'package:react_web/service/react_exceptions.dart';
import 'package:react_web/state/actions_controller.dart';
import 'package:react_web/state/config_controller.dart';
import 'package:react_web/state/control_list_controller.dart';
import 'package:react_web/state/revision_feed.dart';
import 'package:react_web/state/world_overrides_controller.dart';

import 'support/fake_react_client.dart';

Future<void> settle([int ms = 20]) =>
    Future<void>.delayed(Duration(milliseconds: ms));

ServerRevisions revs(int tweaks, {int features = 1, int config = 1}) =>
    ServerRevisions(<RevisionKind, int>{
      RevisionKind.tweaks: tweaks,
      RevisionKind.features: features,
      RevisionKind.config: config,
      RevisionKind.worlds: 1,
      RevisionKind.actions: 1,
      RevisionKind.packs: 1,
    });

void main() {
  late StreamController<ServerSnapshot> snapshots;
  late RevisionFeed feed;

  setUp(() {
    snapshots = StreamController<ServerSnapshot>.broadcast(sync: true);
    feed = RevisionFeed(snapshots.stream, initial: revs(1));
  });

  tearDown(() async {
    feed.dispose();
    await snapshots.close();
  });

  group('RevisionFeed', () {
    test('emits a kind only when its counter moves', () {
      final List<RevisionKind> seen = <RevisionKind>[];
      feed.changes.listen(seen.add);
      feed.update(revs(1));
      feed.update(revs(2));
      feed.update(revs(2, config: 5));
      expect(seen, <RevisionKind>[RevisionKind.tweaks, RevisionKind.config]);
    });

    test('a snapshot without revisions (old plugin) changes nothing', () {
      final List<RevisionKind> seen = <RevisionKind>[];
      feed.changes.listen(seen.add);
      snapshots.add(snapshotWith(seq: 2));
      expect(seen, isEmpty);
      expect(feed[RevisionKind.tweaks], 1);
    });
  });

  group('ControlListController revision refetch', () {
    test('a tweaks bump refetches tweaks once, debounced', () async {
      final FakeReactClient client = FakeReactClient(
        tweaks: <ControlItem>[controlItem('fast-snow')],
      );
      final ControlListController tweaks = ControlListController(
        client,
        isTweaks: true,
        revisions: feed,
        refetchDelay: const Duration(milliseconds: 5),
      );
      await tweaks.load();
      expect(client.count('GET /tweaks'), 1);

      client.tweakList[0] = controlItem('fast-snow', enabled: true);
      feed.update(revs(2));
      feed.update(revs(3));
      feed.update(revs(4));
      await settle();

      expect(client.count('GET /tweaks'), 2);
      expect(client.count('GET /features'), 0);
      expect(tweaks.items.single.enabled, isTrue);
      expect(tweaks.loadedRevision, 4);
    });

    test('a features bump does not refetch tweaks', () async {
      final FakeReactClient client = FakeReactClient(
        tweaks: <ControlItem>[controlItem('a')],
      );
      final ControlListController tweaks = ControlListController(
        client,
        isTweaks: true,
        revisions: feed,
        refetchDelay: Duration.zero,
      );
      await tweaks.load();
      feed.update(revs(1, features: 9));
      await settle();
      expect(client.count('GET /tweaks'), 1);
    });

    test('a bump during a load refetches again after it', () async {
      final FakeReactClient client = FakeReactClient(
        tweaks: <ControlItem>[controlItem('a')],
      );
      final ControlListController tweaks = ControlListController(
        client,
        isTweaks: true,
        revisions: feed,
        refetchDelay: Duration.zero,
      );
      await tweaks.load();
      final Future<void> reload = tweaks.load();
      feed.update(revs(2));
      await reload;
      await settle();
      expect(client.count('GET /tweaks'), 3);
      expect(tweaks.loadedRevision, 2);
    });

    test('a staged knob survives a refetch and is flagged changed', () async {
      final FakeReactClient client = FakeReactClient(
        tweaks: <ControlItem>[
          controlItem('fluids', knobs: <String, Object?>{'rate': 4}),
        ],
      );
      final ControlListController tweaks = ControlListController(
        client,
        isTweaks: true,
        revisions: feed,
        refetchDelay: Duration.zero,
      );
      await tweaks.load();
      tweaks.stageKnob('fluids', 'rate', 7);
      client.tweakList[0] = controlItem(
        'fluids',
        knobs: <String, Object?>{'rate': 5},
      );
      feed.update(revs(2));
      await settle();

      final Knob shown = tweaks.items.single.knobs.single;
      expect(shown.value, 7);
      expect(tweaks.hasPendingEdits, isTrue);
      expect(tweaks.changedOnServer('fluids', 'rate'), isTrue);
      expect(client.calls.where((String c) => c.startsWith('PUT')), isEmpty);
    });

    test(
      'staging sends nothing; commit sends one request with all keys',
      () async {
        final FakeReactClient client = FakeReactClient(
          features: <ControlItem>[
            controlItem('ai', knobs: <String, Object?>{'a': 1, 'b': 2}),
          ],
        );
        final ControlListController features = ControlListController(
          client,
          isTweaks: false,
          revisions: feed,
        );
        await features.load();
        features.stageKnob('ai', 'a', 10);
        features.stageKnob('ai', 'a', 11);
        features.stageKnob('ai', 'b', 20);
        expect(client.calls.where((String c) => c.startsWith('PUT')), isEmpty);

        final Object? error = await features.commitKnobs('ai');

        expect(error, isNull);
        expect(client.calls.where((String c) => c.startsWith('PUT')), <String>[
          'PUT /features/ai/config a,b',
        ]);
        expect(features.hasPendingEdits, isFalse);
        expect(features.items.single.knobs.map((Knob k) => k.value), <Object?>[
          11,
          20,
        ]);
      },
    );

    test('a refetch landing mid-toggle keeps the optimistic value', () async {
      final FakeReactClient client = FakeReactClient(
        tweaks: <ControlItem>[controlItem('snow')],
      );
      final ControlListController tweaks = ControlListController(
        client,
        isTweaks: true,
        revisions: feed,
        refetchDelay: Duration.zero,
      );
      await tweaks.load();
      client.writeGate = Completer<void>();
      final Future<Object?> toggled = tweaks.toggle('snow', true);
      await tweaks.load();
      expect(tweaks.items.single.enabled, isTrue);
      client.writeGate!.complete();
      expect(await toggled, isNull);
      expect(tweaks.items.single.enabled, isTrue);
    });

    test('toggle returns the error after rolling back', () async {
      final FakeReactClient client = FakeReactClient(
        tweaks: <ControlItem>[controlItem('snow')],
      );
      final ControlListController tweaks = ControlListController(
        client,
        isTweaks: true,
      );
      await tweaks.load();
      client.controlError = const ReactForbidden();
      final Object? error = await tweaks.toggle('snow', true);
      expect(error, isA<ReactForbidden>());
      expect(tweaks.items.single.enabled, isFalse);
    });

    test('a disposed controller never writes', () async {
      final FakeReactClient client = FakeReactClient(
        tweaks: <ControlItem>[controlItem('snow')],
      );
      final ControlListController tweaks = ControlListController(
        client,
        isTweaks: true,
      );
      await tweaks.load();
      tweaks.dispose();
      await tweaks.toggle('snow', true);
      expect(client.count('PUT /tweaks/snow'), 0);
    });

    test('notifies listeners on load and toggle', () async {
      final FakeReactClient client = FakeReactClient(
        tweaks: <ControlItem>[controlItem('snow')],
      );
      final ControlListController tweaks = ControlListController(
        client,
        isTweaks: true,
      );
      int notified = 0;
      tweaks.addListener(() => notified++);
      await settle();
      expect(client.count('GET /tweaks'), 1, reason: 'first listener loads');
      final int afterLoad = notified;
      await tweaks.toggle('snow', true);
      expect(notified, greaterThan(afterLoad));
    });
  });

  group('WorldOverridesController', () {
    WorldSettings world(String name, double budget) => WorldSettings(
      key: name,
      name: name,
      pressureMode: PressureMode.values.first,
      budgetMs: budget,
      panicMs: 40,
      releaseMs: 20,
    );

    test('stage keeps typing local; commit sends once', () async {
      final FakeReactClient client = FakeReactClient(
        worlds: <WorldSettings>[world('world', 10)],
      );
      final WorldOverridesController worlds = WorldOverridesController(
        client,
        revisions: feed,
      );
      await worlds.load();
      worlds.stageBudget('world', budgetMs: 1);
      worlds.stageBudget('world', budgetMs: 12);
      worlds.stageBudget('world', budgetMs: 125);
      expect(client.count('PUT /worlds/world'), 0);
      expect(worlds.worlds.single.budgetMs, 125);

      await worlds.commitBudget('world');
      expect(client.count('PUT /worlds/world'), 1);
      expect(client.worldList.single.budgetMs, 125);
      expect(worlds.hasPendingEdits, isFalse);
    });

    test('a worlds bump flags a world being edited', () async {
      final FakeReactClient client = FakeReactClient(
        worlds: <WorldSettings>[world('world', 10)],
      );
      final WorldOverridesController worlds = WorldOverridesController(
        client,
        revisions: feed,
        refetchDelay: Duration.zero,
      );
      await worlds.load();
      worlds.stageBudget('world', budgetMs: 30);
      client.worldList[0] = world('world', 11);
      feed.update(
        ServerRevisions(<RevisionKind, int>{
          ...revs(1).values,
          RevisionKind.worlds: 2,
        }),
      );
      await settle();
      expect(worlds.worlds.single.budgetMs, 30);
      expect(worlds.changedOnServer('world'), isTrue);
    });
  });

  group('ConfigController', () {
    ConfigTree tree(Object? value) => ConfigTree(
      sections: <ConfigSection>[
        ConfigSection(
          name: 'core',
          nodes: <Knob>[
            Knob(
              key: 'tick',
              label: 'Tick',
              type: KnobType.intType,
              value: value,
            ),
          ],
        ),
      ],
    );

    test('a preset is refused while edits are pending', () async {
      final FakeReactClient client = FakeReactClient(config: tree(1));
      final ConfigController config = ConfigController(client);
      await config.load();
      config.edit('tick', 5);

      final Object? refused = await config.applyPreset('balanced');
      expect(refused, isA<ReactPendingEdits>());
      expect(config.hasPendingEdits, isTrue);
      expect(client.count('POST /config/preset/balanced'), 0);

      final Object? applied = await config.applyPreset(
        'balanced',
        discardPending: true,
      );
      expect(applied, isNull);
      expect(client.count('POST /config/preset/balanced'), 1);
      expect(config.hasPendingEdits, isFalse);
    });

    test('a config bump keeps pending edits and flags them', () async {
      final FakeReactClient client = FakeReactClient(config: tree(1));
      final ConfigController config = ConfigController(
        client,
        revisions: feed,
        refetchDelay: Duration.zero,
      );
      await config.load();
      config.edit('tick', 5);
      client.configTree = tree(2);
      feed.update(revs(1, config: 2));
      await settle();
      expect(config.pending, <String, Object?>{'tick': 5});
      expect(config.changedOnServer('tick'), isTrue);
      expect(config.tree.node('tick')!.value, 2);
    });
  });

  group('ActionsController', () {
    test('polls a ticket until done and exposes the outcome', () async {
      final FakeReactClient client = FakeReactClient(name: 'paper');
      client.ticketScript['paper-ticket-1'] = <ActionTicketStatus>[
        const ActionTicketStatus(
          id: 'paper-ticket-1',
          state: ActionTicketState.running,
        ),
        const ActionTicketStatus(
          id: 'paper-ticket-1',
          state: ActionTicketState.done,
          message: 'Collected garbage',
        ),
      ];
      final ActionsController actions = ActionsController(
        client,
        ticketPollDelays: const <Duration>[Duration(milliseconds: 1)],
      );
      await actions.execute('gc', const <String, Object?>{}, false);
      expect(actions.recent.single.state, ActionTicketState.queued);
      await settle(40);
      expect(actions.recent.single.state, ActionTicketState.done);
      expect(actions.recent.single.message, 'Collected garbage');
      expect(client.count('GET /actions/tickets/paper-ticket-1'), 2);
    });

    test('an old plugin without tickets stops polling after one 404', () async {
      final FakeReactClient client = FakeReactClient(name: 'old');
      final ActionsController actions = ActionsController(
        client,
        ticketPollDelays: const <Duration>[Duration(milliseconds: 1)],
      );
      await actions.execute('gc', const <String, Object?>{}, false);
      await settle(30);
      expect(client.count('GET /actions/tickets/old-ticket-1'), 1);
      expect(actions.recent.single.state, ActionTicketState.queued);
    });
  });
}
