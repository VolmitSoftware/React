/// Settings: this browser's preferences and saved servers, in three tabs:
/// General, Servers (with each server's role in Advanced) and Alerts.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';

import '../../localization/reactor_locale.dart';
import '../../localization/reactor_localizations.dart';
import '../../ui/kit/page.dart';
import '../../ui/kit/tabs.dart';
import '../settings.dart';
import '../settings_alerts.dart';
import '../settings_servers.dart';

class SettingsPage extends StatelessWidget {
  const SettingsPage({super.key});

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    return AppPage(
      title: reactorText(ReactorText.settingsTitle),
      tip: reactorText(ReactorText.pageSettingsTip),
      tabs: AppTabs(
        label: reactorText(ReactorText.settingsTitle),
        tabs: <AppTab>[
          AppTab(
            'general',
            reactorText(ReactorText.pageSettingsGeneral),
            const SettingsGeneralTab(),
          ),
          AppTab(
            'servers',
            reactorText(ReactorText.fleetServers),
            const SettingsServersTab(),
          ),
          AppTab(
            'alerts',
            reactorText(ReactorText.commonAlerts),
            const SettingsAlertsTab(),
          ),
        ],
      ),
    );
  }
}
