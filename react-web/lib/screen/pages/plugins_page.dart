/// Plugins (L4): plugin integrations and, for admins, plugin API packs.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';

import '../../localization/reactor_locale.dart';
import '../../localization/reactor_localizations.dart';
import '../../ui/kit/page.dart';
import '../../ui/kit/role_gate.dart';
import '../../ui/kit/tabs.dart';
import '../integrations.dart';
import '../plugin_api_packs.dart';

class PluginsPage extends StatelessWidget {
  const PluginsPage({super.key});

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    return AppPage(
      title: reactorText(ReactorText.pagePluginsTitle),
      tip: reactorText(ReactorText.pagePluginsTip),
      tabs: AppTabs(
        label: reactorText(ReactorText.pagePluginsTitle),
        tabs: <AppTab>[
          AppTab(
            'integrations',
            reactorText(ReactorText.integrationsTitle),
            const IntegrationsScreen(),
          ),
          if (RoleGate.allows(context, RoleNeed.admin))
            AppTab(
              'packs',
              reactorText(ReactorText.pagePluginsApiPacks),
              const PluginApiPacksScreen(),
            ),
        ],
      ),
    );
  }
}
