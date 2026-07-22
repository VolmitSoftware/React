library;

import 'package:jaspr/server.dart';

import 'app/reactor_app.dart';
import 'localization/reactor_localizations.dart';
import 'localization/reactor_overlay_loader.dart';
import 'main.server.options.dart';

Future<void> main() async {
  Jaspr.initializeApp(options: defaultServerOptions);
  await reactorLocalizations.loadOverlayOnce(loadReactorLocalizationOverlay);
  runApp(const ReactorApp());
}
