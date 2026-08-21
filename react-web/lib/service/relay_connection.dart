library;

import 'relay_connection_interface.dart';
import 'relay_connection_stub.dart'
    if (dart.library.js_interop) 'relay_connection_web.dart'
    as platform;

export 'relay_connection_interface.dart';

IRelayConnection createRelayConnection(String relayUrl, String serverId) =>
    platform.createRelayConnection(relayUrl, serverId);
