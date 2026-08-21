library;

import '../model/server_credential.dart';
import 'react_socket_interface.dart';
import 'react_socket_stub.dart'
    if (dart.library.js_interop) 'react_socket_web.dart'
    as platform;

export 'react_socket_interface.dart';

IMetricsSocket createMetricsSocket(ServerCredential cred) =>
    platform.createMetricsSocket(cred);
