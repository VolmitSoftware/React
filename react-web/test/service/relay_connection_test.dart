library;

import 'dart:async';

import 'package:test/test.dart';

import 'package:react_web/model/relay_frame.dart';
import 'package:react_web/service/relay_connection.dart';

void main() {
  group('RelayRpcMux', () {
    late List<RelayFrame> sentFrames;
    late RelayRpcMux mux;

    setUp(() {
      sentFrames = <RelayFrame>[];
      mux = RelayRpcMux(
        serverId: 'srv-1',
        sendFrame: (RelayFrame f) => sentFrames.add(f),
      );
    });

    test(
      'request sends a route frame with method/path/headers and non-null requestId',
      () async {
        final Future<RelayResponse> future = mux.request(
          method: 'GET',
          path: '/api/v1/metrics',
          headers: <String, String>{'Authorization': 'Bearer t'},
        );
        expect(sentFrames, hasLength(1));
        final RelayFrame f = sentFrames[0];
        expect(f.type, equals(RelayFrameType.route));
        expect(f.serverId, equals('srv-1'));
        expect(f.requestId, isNotNull);
        expect(f.requestId, isNotEmpty);
        expect(f.payload?['method'], equals('GET'));
        expect(f.payload?['path'], equals('/api/v1/metrics'));
        final Map<String, String> headers = (f.payload?['headers'] as Map)
            .cast<String, String>();
        expect(headers['Authorization'], equals('Bearer t'));
        mux.onFrame(
          RelayFrame(
            type: RelayFrameType.data,
            requestId: f.requestId,
            payload: <String, dynamic>{
              'status': 200,
              'body': <String, dynamic>{'data': <String, dynamic>{}},
            },
          ),
        );
        await future;
      },
    );

    test(
      'matching data frame completes future with correct status and body',
      () async {
        final Future<RelayResponse> future = mux.request(
          method: 'GET',
          path: '/api/v1/metrics',
          headers: <String, String>{},
        );
        final String reqId = sentFrames[0].requestId!;
        mux.onFrame(
          RelayFrame(
            type: RelayFrameType.data,
            requestId: reqId,
            payload: <String, dynamic>{
              'status': 200,
              'body': <String, dynamic>{
                'data': <String, dynamic>{'tps': 20},
              },
            },
          ),
        );
        final RelayResponse resp = await future;
        expect(resp.status, equals(200));
        expect((resp.body['data'] as Map<String, dynamic>)['tps'], equals(20));
      },
    );

    test(
      'data frame with non-matching requestId leaves future pending',
      () async {
        bool completed = false;
        final Future<RelayResponse> future = mux.request(
          method: 'GET',
          path: '/test',
          headers: <String, String>{},
        );
        unawaited(future.then((_) => completed = true));
        mux.onFrame(
          RelayFrame(
            type: RelayFrameType.data,
            requestId: 'wrong-id',
            payload: <String, dynamic>{
              'status': 200,
              'body': <String, dynamic>{},
            },
          ),
        );
        await Future<void>.delayed(Duration.zero);
        expect(completed, isFalse);
        mux.failAll(
          RelayResponse(
            status: 503,
            body: <String, dynamic>{'error': 'cleanup'},
          ),
        );
      },
    );

    test(
      'error frame without requestId fails all pending with status 503',
      () async {
        final Future<RelayResponse> f1 = mux.request(
          method: 'GET',
          path: '/a',
          headers: <String, String>{},
        );
        final Future<RelayResponse> f2 = mux.request(
          method: 'POST',
          path: '/b',
          headers: <String, String>{},
        );
        mux.onFrame(
          RelayFrame(
            type: RelayFrameType.error,
            payload: <String, dynamic>{'message': 'server offline'},
          ),
        );
        final RelayResponse r1 = await f1;
        final RelayResponse r2 = await f2;
        expect(r1.status, equals(503));
        expect(r2.status, equals(503));
      },
    );

    test(
      'two concurrent requests get distinct requestIds and each completes with own data',
      () async {
        final Future<RelayResponse> f1 = mux.request(
          method: 'GET',
          path: '/x',
          headers: <String, String>{},
        );
        final Future<RelayResponse> f2 = mux.request(
          method: 'GET',
          path: '/y',
          headers: <String, String>{},
        );
        final String id1 = sentFrames[0].requestId!;
        final String id2 = sentFrames[1].requestId!;
        expect(id1, isNot(equals(id2)));
        mux.onFrame(
          RelayFrame(
            type: RelayFrameType.data,
            requestId: id2,
            payload: <String, dynamic>{
              'status': 201,
              'body': <String, dynamic>{'n': 2},
            },
          ),
        );
        mux.onFrame(
          RelayFrame(
            type: RelayFrameType.data,
            requestId: id1,
            payload: <String, dynamic>{
              'status': 200,
              'body': <String, dynamic>{'n': 1},
            },
          ),
        );
        final RelayResponse r1 = await f1;
        final RelayResponse r2 = await f2;
        expect(r1.status, equals(200));
        expect(r1.body['n'], equals(1));
        expect(r2.status, equals(201));
        expect(r2.body['n'], equals(2));
      },
    );
  });
}
