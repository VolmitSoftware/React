library;

import 'package:test/test.dart';

import 'package:react_web/model/action_descriptor.dart';
import 'package:react_web/service/react_client.dart';
import 'package:react_web/service/react_exceptions.dart';
import 'package:react_web/state/actions_controller.dart';

class _FakeActionClient implements IActionClient {
  List<ActionDescriptor> actionsResult = <ActionDescriptor>[];
  bool throwOnActions = false;
  bool throwOnExecute = false;
  String ticketId = 'ticket-abc';
  String ticketStatus = 'queued';

  @override
  Future<List<ActionDescriptor>> actions() async {
    if (throwOnActions) throw const ReactUnavailable('forced');
    return actionsResult;
  }

  @override
  Future<ActionTicket> executeAction(
    String id, {
    required Map<String, Object?> params,
    required bool confirm,
  }) async {
    if (throwOnExecute) throw const ReactConflict();
    return ActionTicket(ticketId: ticketId, status: ticketStatus);
  }
}

void main() {
  group('ActionsController.load', () {
    test('populates actions and clears loading on success', () async {
      final _FakeActionClient client = _FakeActionClient();
      client.actionsResult = <ActionDescriptor>[
        const ActionDescriptor(
          id: 'gc',
          name: 'GC',
          description: 'Run GC',
          destructive: false,
        ),
      ];
      int notifyCount = 0;
      final ActionsController controller = ActionsController(
        client,
        onChange: () => notifyCount++,
      );

      await controller.load();

      expect(controller.actions.length, equals(1));
      expect(controller.actions[0].id, equals('gc'));
      expect(controller.loading, isFalse);
      expect(controller.error, isNull);
      expect(notifyCount, greaterThanOrEqualTo(2));
    });

    test('sets error and calls onError on failure', () async {
      final _FakeActionClient client = _FakeActionClient();
      client.throwOnActions = true;
      Object? capturedError;
      int notifyCount = 0;
      final ActionsController controller = ActionsController(
        client,
        onChange: () => notifyCount++,
        onError: (Object e) => capturedError = e,
      );

      await controller.load();

      expect(controller.actions, isEmpty);
      expect(controller.loading, isFalse);
      expect(controller.error, isA<ReactUnavailable>());
      expect(capturedError, isA<ReactUnavailable>());
      expect(notifyCount, greaterThanOrEqualTo(2));
    });
  });

  group('ActionsController.execute', () {
    test('success appends to recent and ticketId matches', () async {
      final _FakeActionClient client = _FakeActionClient();
      client.actionsResult = <ActionDescriptor>[
        const ActionDescriptor(
          id: 'gc',
          name: 'GC',
          description: 'Run GC',
          destructive: false,
        ),
      ];
      client.ticketId = 'tkt-123';
      client.ticketStatus = 'queued';
      final ActionsController controller = ActionsController(client);
      await controller.load();

      await controller.execute('gc', <String, Object?>{}, false);

      expect(controller.recent.length, equals(1));
      expect(controller.recent.first.ticketId, equals('tkt-123'));
      expect(controller.recent.first.actionId, equals('gc'));
    });

    test('success grows recent list', () async {
      final _FakeActionClient client = _FakeActionClient();
      client.actionsResult = <ActionDescriptor>[
        const ActionDescriptor(
          id: 'gc',
          name: 'GC',
          description: 'Run GC',
          destructive: false,
        ),
      ];
      final ActionsController controller = ActionsController(client);
      await controller.load();

      await controller.execute('gc', <String, Object?>{}, false);
      await controller.execute('gc', <String, Object?>{}, false);

      expect(controller.recent.length, equals(2));
    });

    test('failure calls onError and leaves recent unchanged', () async {
      final _FakeActionClient client = _FakeActionClient();
      client.actionsResult = <ActionDescriptor>[
        const ActionDescriptor(
          id: 'gc',
          name: 'GC',
          description: 'Run GC',
          destructive: false,
        ),
      ];
      client.throwOnExecute = true;
      Object? capturedError;
      final ActionsController controller = ActionsController(
        client,
        onError: (Object e) => capturedError = e,
      );
      await controller.load();

      await controller.execute('gc', <String, Object?>{}, false);

      expect(controller.recent, isEmpty);
      expect(capturedError, isA<ReactConflict>());
    });

    test('caps recent at 20 by dropping oldest', () async {
      final _FakeActionClient client = _FakeActionClient();
      client.actionsResult = <ActionDescriptor>[
        const ActionDescriptor(
          id: 'gc',
          name: 'GC',
          description: 'Run GC',
          destructive: false,
        ),
      ];
      final ActionsController controller = ActionsController(client);
      await controller.load();

      for (int i = 0; i < 25; i++) {
        client.ticketId = 'tkt-$i';
        await controller.execute('gc', <String, Object?>{}, false);
      }

      expect(controller.recent.length, equals(20));
      expect(controller.recent.first.ticketId, equals('tkt-24'));
    });
  });
}
