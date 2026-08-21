import 'package:react_web_relay/reactor_relay.dart';
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

Map<String, dynamic> _routePayload({String method = 'GET', Object? body}) {
  return <String, dynamic>{
    'method': method,
    'path': '/api/v1/identity',
    'headers': <String, dynamic>{'Authorization': 'Bearer test'},
    'body': ?body,
  };
}

RelayFrame _route(String requestId, {String method = 'GET', Object? body}) {
  return RelayFrame(
    type: RelayFrameType.route,
    serverId: 'S1',
    requestId: requestId,
    payload: _routePayload(method: method, body: body),
  );
}

RelayFrame _response(String brokerRequestId, String marker) {
  return RelayFrame(
    type: RelayFrameType.data,
    serverId: 'S1',
    requestId: brokerRequestId,
    payload: <String, dynamic>{
      'status': 200,
      'body': <String, dynamic>{'marker': marker},
    },
  );
}

int _status(RelayFrame frame) => frame.payload!['status'] as int;

void main() {
  group('RelayBroker', () {
    test('rewrites broker request ID and restores the browser request ID', () {
      final RelayBroker broker = RelayBroker();
      final _FakeSink agent = _FakeSink();
      final _FakeSink app = _FakeSink();
      broker.registerServer('S1', agent);
      broker.subscribeApp('S1', app);

      expect(broker.routeFromApp('S1', app, _route('client-1')), isTrue);
      expect(agent.sent, hasLength(1));
      final String brokerRequestId = agent.sent.single.requestId!;
      expect(brokerRequestId, isNot('client-1'));
      expect(broker.pendingRequestCount, 1);

      expect(
        broker.routeFromServer('S1', agent, _response(brokerRequestId, 'one')),
        isTrue,
      );

      expect(app.sent, hasLength(1));
      expect(app.sent.single.requestId, 'client-1');
      expect(app.sent.single.payload!['body'], <String, dynamic>{
        'marker': 'one',
      });
      expect(broker.pendingRequestCount, 0);
    });

    test('isolates two apps that use the same client request ID', () {
      final RelayBroker broker = RelayBroker();
      final _FakeSink agent = _FakeSink();
      final _FakeSink appA = _FakeSink();
      final _FakeSink appB = _FakeSink();
      broker.registerServer('S1', agent);
      broker.subscribeApp('S1', appA);
      broker.subscribeApp('S1', appB);

      expect(broker.routeFromApp('S1', appA, _route('same-id')), isTrue);
      expect(broker.routeFromApp('S1', appB, _route('same-id')), isTrue);
      expect(agent.sent, hasLength(2));
      final String brokerIdA = agent.sent[0].requestId!;
      final String brokerIdB = agent.sent[1].requestId!;
      expect(brokerIdA, isNot(brokerIdB));
      expect(brokerIdA, isNot('same-id'));
      expect(brokerIdB, isNot('same-id'));

      broker.routeFromServer('S1', agent, _response(brokerIdB, 'B'));
      expect(appA.sent, isEmpty);
      expect(appB.sent, hasLength(1));
      expect(appB.sent.single.requestId, 'same-id');
      expect(appB.sent.single.payload!['body'], <String, dynamic>{
        'marker': 'B',
      });

      broker.routeFromServer('S1', agent, _response(brokerIdA, 'A'));
      expect(appA.sent, hasLength(1));
      expect(appA.sent.single.requestId, 'same-id');
      expect(appA.sent.single.payload!['body'], <String, dynamic>{
        'marker': 'A',
      });
      expect(appB.sent, hasLength(1));
    });

    test('drops unknown and already completed broker response IDs', () {
      final RelayBroker broker = RelayBroker();
      final _FakeSink agent = _FakeSink();
      final _FakeSink app = _FakeSink();
      broker.registerServer('S1', agent);
      broker.subscribeApp('S1', app);

      expect(
        broker.routeFromServer('S1', agent, _response('unknown', 'bad')),
        isFalse,
      );
      expect(app.sent, isEmpty);

      broker.routeFromApp('S1', app, _route('client-1'));
      final String brokerRequestId = agent.sent.single.requestId!;
      expect(
        broker.routeFromServer(
          'S1',
          agent,
          _response(brokerRequestId, 'first'),
        ),
        isTrue,
      );
      expect(
        broker.routeFromServer(
          'S1',
          agent,
          _response(brokerRequestId, 'second'),
        ),
        isFalse,
      );
      expect(app.sent, hasLength(1));
    });

    test('rejects a response from a replaced agent connection', () {
      final RelayBroker broker = RelayBroker();
      final _FakeSink oldAgent = _FakeSink();
      final _FakeSink newAgent = _FakeSink();
      final _FakeSink app = _FakeSink();
      broker.registerServer('S1', oldAgent);
      broker.subscribeApp('S1', app);
      broker.routeFromApp('S1', app, _route('client-1'));
      final String brokerRequestId = oldAgent.sent.single.requestId!;

      broker.registerServer('S1', newAgent);
      expect(oldAgent.closed, isTrue);
      expect(app.sent, hasLength(1));
      expect(_status(app.sent.single), 503);
      expect(
        broker.routeFromServer(
          'S1',
          oldAgent,
          _response(brokerRequestId, 'late'),
        ),
        isFalse,
      );
      expect(app.sent, hasLength(1));
    });

    test('removes every pending request when an app disconnects', () {
      final RelayBroker broker = RelayBroker();
      final _FakeSink agent = _FakeSink();
      final _FakeSink app = _FakeSink();
      broker.registerServer('S1', agent);
      broker.subscribeApp('S1', app);
      broker.routeFromApp('S1', app, _route('one'));
      broker.routeFromApp('S1', app, _route('two'));
      expect(broker.pendingRequestCount, 2);

      broker.unregisterApp(app);

      expect(broker.pendingRequestCount, 0);
      expect(broker.appSessionCount, 0);
      for (final RelayFrame routed in agent.sent) {
        expect(
          broker.routeFromServer(
            'S1',
            agent,
            _response(routed.requestId!, 'late'),
          ),
          isFalse,
        );
      }
      expect(app.sent, isEmpty);
    });

    test('fails pending requests when the agent disconnects', () {
      final RelayBroker broker = RelayBroker();
      final _FakeSink agent = _FakeSink();
      final _FakeSink app = _FakeSink();
      broker.registerServer('S1', agent);
      broker.subscribeApp('S1', app);
      broker.routeFromApp('S1', app, _route('client-1'));

      broker.unregisterServer('S1', agent);

      expect(broker.pendingRequestCount, 0);
      expect(app.sent, hasLength(1));
      expect(app.sent.single.requestId, 'client-1');
      expect(_status(app.sent.single), 503);
    });

    test('enforces the per-app pending request limit', () {
      const RelayLimits limits = RelayLimits(
        maxPendingRequestsPerApp: 1,
        maxPendingRequestsGlobal: 2,
      );
      final RelayBroker broker = RelayBroker(limits: limits);
      final _FakeSink agent = _FakeSink();
      final _FakeSink app = _FakeSink();
      broker.registerServer('S1', agent);
      broker.subscribeApp('S1', app);

      expect(broker.routeFromApp('S1', app, _route('one')), isTrue);
      expect(broker.routeFromApp('S1', app, _route('two')), isFalse);

      expect(agent.sent, hasLength(1));
      expect(app.sent, hasLength(1));
      expect(app.sent.single.requestId, 'two');
      expect(_status(app.sent.single), 429);
      broker.unregisterApp(app);
    });

    test('enforces the global pending request limit across apps', () {
      const RelayLimits limits = RelayLimits(
        maxPendingRequestsPerApp: 1,
        maxPendingRequestsGlobal: 1,
      );
      final RelayBroker broker = RelayBroker(limits: limits);
      final _FakeSink agent = _FakeSink();
      final _FakeSink appA = _FakeSink();
      final _FakeSink appB = _FakeSink();
      broker.registerServer('S1', agent);
      broker.subscribeApp('S1', appA);
      broker.subscribeApp('S1', appB);

      expect(broker.routeFromApp('S1', appA, _route('one')), isTrue);
      expect(broker.routeFromApp('S1', appB, _route('two')), isFalse);

      expect(agent.sent, hasLength(1));
      expect(appA.sent, isEmpty);
      expect(appB.sent, hasLength(1));
      expect(_status(appB.sent.single), 503);
      broker.unregisterApp(appA);
      broker.unregisterApp(appB);
    });

    test('rejects duplicate pending IDs within one app connection', () {
      final RelayBroker broker = RelayBroker();
      final _FakeSink agent = _FakeSink();
      final _FakeSink app = _FakeSink();
      broker.registerServer('S1', agent);
      broker.subscribeApp('S1', app);

      expect(broker.routeFromApp('S1', app, _route('same')), isTrue);
      expect(broker.routeFromApp('S1', app, _route('same')), isFalse);

      expect(agent.sent, hasLength(1));
      expect(app.sent, hasLength(1));
      expect(_status(app.sent.single), 409);
      broker.unregisterApp(app);
    });

    test(
      'expires pending requests and restores the browser request ID',
      () async {
        const RelayLimits limits = RelayLimits(
          requestTimeout: Duration(milliseconds: 20),
        );
        final RelayBroker broker = RelayBroker(limits: limits);
        final _FakeSink agent = _FakeSink();
        final _FakeSink app = _FakeSink();
        broker.registerServer('S1', agent);
        broker.subscribeApp('S1', app);
        broker.routeFromApp('S1', app, _route('client-timeout'));

        await Future<void>.delayed(const Duration(milliseconds: 60));

        expect(broker.pendingRequestCount, 0);
        expect(app.sent, hasLength(1));
        expect(app.sent.single.requestId, 'client-timeout');
        expect(_status(app.sent.single), 504);
      },
    );

    test('requires mutating bodies to be pre-encoded JSON strings', () {
      final RelayBroker broker = RelayBroker();
      final _FakeSink agent = _FakeSink();
      final _FakeSink app = _FakeSink();
      broker.registerServer('S1', agent);
      broker.subscribeApp('S1', app);

      expect(
        broker.routeFromApp(
          'S1',
          app,
          _route('bad-body', method: 'PUT', body: <String, dynamic>{'x': 1}),
        ),
        isFalse,
      );

      expect(agent.sent, isEmpty);
      expect(_status(app.sent.single), 400);
      final Map<String, dynamic> responseBody =
          app.sent.single.payload!['body'] as Map<String, dynamic>;
      final Map<String, dynamic> error =
          responseBody['error'] as Map<String, dynamic>;
      expect(error['message'], contains('JSON-encoded string'));
    });

    test('preserves an encoded mutating request body exactly', () {
      final RelayBroker broker = RelayBroker();
      final _FakeSink agent = _FakeSink();
      final _FakeSink app = _FakeSink();
      broker.registerServer('S1', agent);
      broker.subscribeApp('S1', app);
      const String body = '{"enabled":true}';

      expect(
        broker.routeFromApp(
          'S1',
          app,
          _route('put', method: 'PUT', body: body),
        ),
        isTrue,
      );

      expect(agent.sent.single.payload!['body'], body);
      broker.unregisterApp(app);
    });

    test('offline subscription and request return scoped errors', () {
      final RelayBroker broker = RelayBroker();
      final _FakeSink app = _FakeSink();
      broker.subscribeApp('S1', app);
      expect(app.sent, hasLength(1));
      expect(app.sent.first.type, RelayFrameType.error);

      expect(broker.routeFromApp('S1', app, _route('offline')), isFalse);

      expect(app.sent, hasLength(2));
      expect(app.sent.last.requestId, 'offline');
      expect(_status(app.sent.last), 503);
    });

    test('online and session counts reflect registrations', () {
      final RelayBroker broker = RelayBroker();
      final _FakeSink agentA = _FakeSink();
      final _FakeSink agentB = _FakeSink();
      final _FakeSink app = _FakeSink();
      expect(broker.onlineCount, 0);
      expect(broker.appSessionCount, 0);

      broker.registerServer('A', agentA);
      broker.registerServer('B', agentB);
      broker.subscribeApp('A', app);

      expect(broker.onlineCount, 2);
      expect(broker.appSessionCount, 1);
      broker.unregisterServer('A', agentA);
      broker.unregisterApp(app);
      expect(broker.onlineCount, 1);
      expect(broker.appSessionCount, 0);
    });
  });
}
