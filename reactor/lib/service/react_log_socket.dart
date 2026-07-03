library;

import '../model/server_credential.dart';
import 'react_log_socket_interface.dart';
import 'react_log_socket_stub.dart'
    if (dart.library.js_interop) 'react_log_socket_web.dart' as platform;

export 'react_log_socket_interface.dart';

ILogSocket createLogSocket(ServerCredential cred) =>
    platform.createLogSocket(cred);
