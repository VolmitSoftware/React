final class RelayLimits {
  static const int defaultMaxFrameBytes = 1024 * 1024;

  final int maxFrameBytes;
  final int maxMessagesPerWindow;
  final Duration messageRateWindow;
  final int maxPendingRequestsPerApp;
  final int maxPendingRequestsGlobal;
  final int maxOutboundQueueMessages;
  final Duration handshakeTimeout;
  final Duration subscriptionTimeout;
  final Duration requestTimeout;
  final Duration pingInterval;

  const RelayLimits({
    this.maxFrameBytes = defaultMaxFrameBytes,
    this.maxMessagesPerWindow = 120,
    this.messageRateWindow = const Duration(seconds: 1),
    this.maxPendingRequestsPerApp = 64,
    this.maxPendingRequestsGlobal = 4096,
    this.maxOutboundQueueMessages = 128,
    this.handshakeTimeout = const Duration(seconds: 10),
    this.subscriptionTimeout = const Duration(seconds: 10),
    this.requestTimeout = const Duration(seconds: 10),
    this.pingInterval = const Duration(seconds: 30),
  });

  void validate() {
    if (maxFrameBytes < 1024 || maxFrameBytes > 16 * 1024 * 1024) {
      throw ArgumentError.value(maxFrameBytes, 'maxFrameBytes');
    }
    if (maxMessagesPerWindow < 1 || maxMessagesPerWindow > 10000) {
      throw ArgumentError.value(maxMessagesPerWindow, 'maxMessagesPerWindow');
    }
    if (messageRateWindow <= Duration.zero ||
        messageRateWindow > const Duration(minutes: 1)) {
      throw ArgumentError.value(messageRateWindow, 'messageRateWindow');
    }
    if (maxPendingRequestsPerApp < 1 || maxPendingRequestsPerApp > 4096) {
      throw ArgumentError.value(
        maxPendingRequestsPerApp,
        'maxPendingRequestsPerApp',
      );
    }
    if (maxPendingRequestsGlobal < maxPendingRequestsPerApp ||
        maxPendingRequestsGlobal > 100000) {
      throw ArgumentError.value(
        maxPendingRequestsGlobal,
        'maxPendingRequestsGlobal',
      );
    }
    if (maxOutboundQueueMessages < 1 || maxOutboundQueueMessages > 4096) {
      throw ArgumentError.value(
        maxOutboundQueueMessages,
        'maxOutboundQueueMessages',
      );
    }
    _validateDuration(handshakeTimeout, 'handshakeTimeout');
    _validateDuration(subscriptionTimeout, 'subscriptionTimeout');
    _validateDuration(requestTimeout, 'requestTimeout');
    _validateDuration(pingInterval, 'pingInterval');
  }

  void _validateDuration(Duration value, String name) {
    if (value <= Duration.zero || value > const Duration(hours: 1)) {
      throw ArgumentError.value(value, name);
    }
  }
}
