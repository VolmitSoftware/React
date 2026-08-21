import 'package:react_web_relay/reactor_relay.dart';
import 'package:test/test.dart';

void main() {
  group('RelayLimits', () {
    test('default limits are valid', () {
      expect(() => const RelayLimits().validate(), returnsNormally);
    });

    test('rejects a global pending limit below the per-app limit', () {
      expect(
        () => const RelayLimits(
          maxPendingRequestsPerApp: 2,
          maxPendingRequestsGlobal: 1,
        ).validate(),
        throwsArgumentError,
      );
    });

    test('rejects unsafe frame, rate, queue, and timeout bounds', () {
      expect(
        () => const RelayLimits(maxFrameBytes: 100).validate(),
        throwsArgumentError,
      );
      expect(
        () => const RelayLimits(maxMessagesPerWindow: 0).validate(),
        throwsArgumentError,
      );
      expect(
        () => const RelayLimits(maxOutboundQueueMessages: 0).validate(),
        throwsArgumentError,
      );
      expect(
        () => const RelayLimits(handshakeTimeout: Duration.zero).validate(),
        throwsArgumentError,
      );
    });
  });
}
