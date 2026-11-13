library;

import 'package:test/test.dart';

import 'package:react_web/model/log_line.dart';
import 'package:react_web/model/relay_frame.dart';
import 'package:react_web/model/server_credential.dart';
import 'package:react_web/model/server_revisions.dart';

void main() {
  group('ServerRevisions', () {
    test('reads data.revisions and a top-level map', () {
      final ServerRevisions? inner = ServerRevisions.fromEnvelope(
        <String, dynamic>{
          'data': <String, dynamic>{
            'revisions': <String, dynamic>{'tweaks': 4},
          },
        },
      );
      final ServerRevisions? outer = ServerRevisions.fromEnvelope(
        <String, dynamic>{
          'data': <String, dynamic>{},
          'revisions': <String, dynamic>{'config': 2},
        },
      );
      expect(inner![RevisionKind.tweaks], 4);
      expect(outer![RevisionKind.config], 2);
      expect(
        ServerRevisions.fromEnvelope(<String, dynamic>{
          'data': <String, dynamic>{},
        }),
        isNull,
      );
    });

    test('changedSince ignores kinds missing on either side', () {
      const ServerRevisions before = ServerRevisions(<RevisionKind, int>{
        RevisionKind.tweaks: 1,
      });
      const ServerRevisions after = ServerRevisions(<RevisionKind, int>{
        RevisionKind.tweaks: 2,
        RevisionKind.config: 9,
      });
      expect(after.changedSince(before), <RevisionKind>{RevisionKind.tweaks});
      expect(after.changedSince(null), isEmpty);
    });
  });

  group('LogBatch', () {
    test('parses batched frames with dropped', () {
      final LogBatch batch = LogBatch.fromFrame(<String, dynamic>{
        'type': 'logs',
        'lines': <Object?>[
          <String, dynamic>{'seq': 7, 'line': 'a'},
          <String, dynamic>{'seq': 8, 'line': 'b'},
        ],
        'dropped': 3,
      })!;
      expect(batch.lines.map((LogLine l) => l.seq), <int>[7, 8]);
      expect(batch.dropped, 3);
    });

    test('parses old single frames with and without seq', () {
      final LogBatch old = LogBatch.fromFrame(<String, dynamic>{
        'type': 'log',
        'line': 'x',
      })!;
      final LogBatch seq = LogBatch.fromFrame(<String, dynamic>{
        'type': 'log',
        'line': 'y',
        'seq': 3,
      })!;
      expect(old.lines.single.seq, isNull);
      expect(seq.lines.single.seq, 3);
      expect(LogBatch.fromFrame(<String, dynamic>{'type': 'ping'}), isNull);
    });
  });

  group('RelayFrame status', () {
    test('online is read at the top level or in payload', () {
      expect(
        RelayFrame.decode('{"type":"status","serverId":"fp","online":true}')!
            .online,
        isTrue,
      );
      expect(
        RelayFrame.decode('{"type":"status","payload":{"online":false}}')!
            .online,
        isFalse,
      );
      expect(RelayFrame.decode('{"type":"data"}')!.online, isNull);
    });
  });

  group('ServerCredential identity', () {
    const ServerCredential dual = ServerCredential(
      id: 'a',
      label: 'A',
      host: 'h',
      port: 1,
      bearer: 'tok.sig',
      relayUrl: 'wss://relay',
      fingerprint: 'fp',
      role: 'viewer',
    );

    test('token id, transport and role round-trip', () {
      expect(dual.tokenId, 'tok');
      expect(dual.transport, ServerTransport.dual);
      expect(ServerCredential.fromJson(dual.toJson()).role, 'viewer');
      expect(dual.withId('b').id, 'b');
      expect(dual.withId('b').bearer, 'tok.sig');
    });

    test('the same server means the same fingerprint, whatever the token', () {
      expect(dual.sameServerAs(dual.withId('b')), isTrue);
      expect(dual.sameServerAs(dual.copyWith(bearer: 'other.sig')), isTrue);
      expect(dual.sameServerAs(dual.copyWith(fingerprint: 'other')), isFalse);
    });
  });
}
