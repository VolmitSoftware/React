import 'package:reactor_relay/reactor_relay.dart';
import 'package:test/test.dart';

class _FakeSink implements RelaySink {
  final List<RelayFrame> sent = <RelayFrame>[];
  bool closed = false;
  String? closeMessage;

  @override
  void send(RelayFrame frame) {
    sent.add(frame);
  }

  @override
  void closeWithError(String message) {
    closed = true;
    closeMessage = message;
  }
}

void main() {
  group('RelayBroker', () {
    late RelayBroker broker;

    setUp(() {
      broker = RelayBroker();
    });

    test('routes server data frame to subscribed app only for matching serverId', () {
      final _FakeSink agentS1 = _FakeSink();
      final _FakeSink agentS2 = _FakeSink();
      final _FakeSink appA = _FakeSink();

      broker.registerServer('S1', agentS1);
      broker.registerServer('S2', agentS2);
      broker.subscribeApp('S1', appA);

      final RelayFrame dataFrame = RelayFrame(
        type: RelayFrameType.data,
        serverId: 'S1',
        requestId: 'r1',
        payload: <String, dynamic>{'val': 42},
      );

      broker.routeFromServer('S1', dataFrame);

      expect(appA.sent, hasLength(1));
      expect(appA.sent.first.type, equals(RelayFrameType.data));

      broker.routeFromServer('S2', RelayFrame(
        type: RelayFrameType.data,
        serverId: 'S2',
        requestId: 'r2',
        payload: <String, dynamic>{'val': 99},
      ));

      expect(appA.sent, hasLength(1));
    });

    test('routeFromApp forwards route frame to correct agent with same requestId and payload', () {
      final _FakeSink agentS1 = _FakeSink();
      final _FakeSink agentS2 = _FakeSink();

      broker.registerServer('S1', agentS1);
      broker.registerServer('S2', agentS2);

      final Map<String, dynamic> payload = <String, dynamic>{'method': 'ping'};
      final RelayFrame routeFrame = RelayFrame(
        type: RelayFrameType.route,
        serverId: 'S1',
        requestId: 'req-abc',
        payload: payload,
      );

      broker.routeFromApp('S1', routeFrame);

      expect(agentS1.sent, hasLength(1));
      expect(agentS1.sent.first.requestId, equals('req-abc'));
      expect(agentS1.sent.first.payload, same(payload));
      expect(agentS2.sent, isEmpty);
    });

    test('subscribing to offline server sends error frame immediately', () {
      final _FakeSink appA = _FakeSink();

      broker.subscribeApp('OFFLINE', appA);

      expect(appA.sent, hasLength(1));
      final RelayFrame errorFrame = appA.sent.first;
      expect(errorFrame.type, equals(RelayFrameType.error));
      expect(errorFrame.serverId, equals('OFFLINE'));
      expect(errorFrame.payload?['message'], equals('server offline'));
    });

    test('subscription recorded for offline server receives frames after server registers', () {
      final _FakeSink appA = _FakeSink();
      final _FakeSink agentS1 = _FakeSink();

      broker.subscribeApp('S1', appA);

      expect(appA.sent, hasLength(1));
      expect(appA.sent.first.type, equals(RelayFrameType.error));

      broker.registerServer('S1', agentS1);

      final RelayFrame dataFrame = RelayFrame(
        type: RelayFrameType.data,
        serverId: 'S1',
        requestId: 'r1',
        payload: <String, dynamic>{'k': 'v'},
      );

      broker.routeFromServer('S1', dataFrame);

      expect(appA.sent, hasLength(2));
      expect(appA.sent.last.type, equals(RelayFrameType.data));
    });

    test('routeFromApp after unregisterServer does not reach old agent', () {
      final _FakeSink agentS1 = _FakeSink();

      broker.registerServer('S1', agentS1);
      broker.unregisterServer('S1', agentS1);

      broker.routeFromApp('S1', RelayFrame(
        type: RelayFrameType.route,
        serverId: 'S1',
        requestId: 'r1',
        payload: <String, dynamic>{},
      ));

      expect(agentS1.sent, isEmpty);
      expect(broker.isOnline('S1'), isFalse);
    });

    test('re-registering a server-id closes previous agent sink with error', () {
      final _FakeSink oldAgent = _FakeSink();
      final _FakeSink newAgent = _FakeSink();

      broker.registerServer('S1', oldAgent);
      broker.registerServer('S1', newAgent);

      expect(oldAgent.closed, isTrue);
      expect(oldAgent.closeMessage, equals('replaced by new registration'));
      expect(broker.isOnline('S1'), isTrue);
    });

    test('isOnline returns false before registration and true after', () {
      expect(broker.isOnline('X'), isFalse);
      final _FakeSink agent = _FakeSink();
      broker.registerServer('X', agent);
      expect(broker.isOnline('X'), isTrue);
    });

    test('onlineCount reflects registered servers', () {
      final _FakeSink agentA = _FakeSink();
      final _FakeSink agentB = _FakeSink();
      expect(broker.onlineCount, equals(0));
      broker.registerServer('A', agentA);
      broker.registerServer('B', agentB);
      expect(broker.onlineCount, equals(2));
      broker.unregisterServer('A', agentA);
      expect(broker.onlineCount, equals(1));
    });

    test('unsubscribeApp stops delivery of future server frames', () {
      final _FakeSink agentS1 = _FakeSink();
      final _FakeSink appA = _FakeSink();

      broker.registerServer('S1', agentS1);
      broker.subscribeApp('S1', appA);
      broker.unsubscribeApp('S1', appA);

      broker.routeFromServer('S1', RelayFrame(
        type: RelayFrameType.data,
        serverId: 'S1',
        requestId: 'r1',
        payload: <String, dynamic>{},
      ));

      expect(appA.sent, isEmpty);
    });

    test('unregisterServer only removes if current sink matches', () {
      final _FakeSink agentOld = _FakeSink();
      final _FakeSink agentNew = _FakeSink();

      broker.registerServer('S1', agentOld);
      broker.registerServer('S1', agentNew);

      broker.unregisterServer('S1', agentOld);

      expect(broker.isOnline('S1'), isTrue);
    });
  });
}
