import 'package:react_web_relay/reactor_relay.dart';
import 'package:test/test.dart';

void main() {
  group('RelayLimits', () {
    test('default limits are valid', () {
      expect(() => const RelayLimits().validate(), returnsNormally);
    });

    test('agents get separate, larger rate and queue bounds than apps', () {
      const RelayLimits limits = RelayLimits();
      expect(limits.maxAppMessagesPerWindow, 240);
      expect(limits.maxAgentMessagesPerWindow, 5000);
      expect(limits.maxOutboundQueueMessages, 128);
      expect(limits.maxAgentOutboundQueueMessages, 4096);
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
        () => const RelayLimits(maxAppMessagesPerWindow: 0).validate(),
        throwsArgumentError,
      );
      expect(
        () => const RelayLimits(maxAgentMessagesPerWindow: 0).validate(),
        throwsArgumentError,
      );
      expect(
        () => const RelayLimits(maxAgentOutboundQueueMessages: 0).validate(),
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
