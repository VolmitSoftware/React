/// Incidents (L3): the incident state, the scheduler pressure tiles and the
/// latest incidents in one page.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';

import '../../localization/reactor_locale.dart';
import '../../localization/reactor_localizations.dart';
import '../../ui/kit/page.dart';
import '../incidents.dart';

class IncidentsPage extends StatelessWidget {
  const IncidentsPage({super.key});

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    return AppPage(
      title: reactorText(ReactorText.incidentsTitle),
      tip: reactorText(ReactorText.incidentsSubtitle),
      children: const <Widget>[IncidentsScreen()],
    );
  }
}
