library;

import 'package:react_web_relay/src/relay_frame.dart';

abstract interface class RelaySink {
  void send(RelayFrame frame);
  void closeWithError(String message);
}
