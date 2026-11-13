/// Overview (L2): a server's vitals and incident state.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';

import '../../localization/reactor_locale.dart';
import '../../localization/reactor_localizations.dart';
import '../../ui/kit/page.dart';
import '../monitor_parts.dart';
import '../overview.dart';

class OverviewPage extends StatelessWidget {
  const OverviewPage({super.key});

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    return AppPage(
      title: reactorText(ReactorText.overviewTitle),
      tip: reactorText(ReactorText.overviewSubtitle),
      children: <Widget>[OverviewScreen(serverId: currentServerId(context))],
    );
  }
}
