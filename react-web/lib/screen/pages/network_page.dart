/// Network: every server behind a Velocity or BungeeCord proxy.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';

import '../../localization/reactor_locale.dart';
import '../../localization/reactor_localizations.dart';
import '../../ui/kit/page.dart';
import '../network.dart';

class NetworkPage extends StatelessWidget {
  const NetworkPage({super.key});

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    return AppPage(
      title: reactorText(ReactorText.pageNetworkTitle),
      tip: reactorText(ReactorText.pageNetworkTip),
      children: const <Widget>[NetworkScreen()],
    );
  }
}
