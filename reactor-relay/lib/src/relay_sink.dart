library;

import 'package:reactor_relay/src/relay_frame.dart';

abstract interface class RelaySink {
  void send(RelayFrame frame);
  void closeWithError(String message);
}
