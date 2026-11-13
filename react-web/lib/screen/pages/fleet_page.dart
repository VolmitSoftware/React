/// Servers: every paired server in a table, and one metric compared
/// across them.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';

import '../../localization/reactor_locale.dart';
import '../../localization/reactor_localizations.dart';
import '../../ui/kit/page.dart';
import '../../ui/kit/tabs.dart';
import '../comparison.dart';
import '../fleet_dashboard.dart';

class FleetPage extends StatelessWidget {
  const FleetPage({super.key});

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    return AppPage(
      title: reactorText(ReactorText.pageFleetServers),
      tip: reactorText(ReactorText.pageFleetTip),
      tabs: AppTabs(
        label: reactorText(ReactorText.pageFleetServers),
        tabs: <AppTab>[
          AppTab(
            'servers',
            reactorText(ReactorText.pageFleetServers),
            const FleetDashboardScreen(),
          ),
          AppTab(
            'compare',
            reactorText(ReactorText.pageFleetCompare),
            const ComparisonScreen(),
          ),
        ],
      ),
    );
  }
}
