library;

import 'package:jaspr/server.dart';

import 'app/reactor_app.dart';
import 'main.server.options.dart';

void main() {
  Jaspr.initializeApp(options: defaultServerOptions);
  runApp(const ReactorApp());
}
