/// Plugins (L4), or Mods on a modded server: what is loaded, integrations
/// and, for admins, plugin API packs.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/jaspr.dart' show ListenableBuilder;

import '../../localization/reactor_locale.dart';
import '../../localization/reactor_localizations.dart';
import '../../state/server_identity.dart';
import '../../state/server_session_scope.dart';
import '../../ui/kit/page.dart';
import '../../ui/kit/role_gate.dart';
import '../../ui/kit/tabs.dart';
import '../integrations.dart';
import '../owners.dart';
import '../plugin_api_packs.dart';

class PluginsPage extends StatelessWidget {
  const PluginsPage({super.key});

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final ServerIdentity? identity = ServerSessionScope.of(context)?.identity;
    if (identity == null) return _page(context, modded: false);
    return ListenableBuilder(
      listenable: identity,
      builder: (BuildContext context) =>
          _page(context, modded: identity.modded),
    );
  }

  Widget _page(BuildContext context, {required bool modded}) {
    final ReactorText title = modded
        ? ReactorText.pageModsTitle
        : ReactorText.pagePluginsTitle;
    return AppPage(
      title: reactorText(title),
      tip: reactorText(
        modded ? ReactorText.pageModsTip : ReactorText.pagePluginsTip,
      ),
      tabs: AppTabs(
        label: reactorText(title),
        tabs: <AppTab>[
          AppTab(
            'installed',
            reactorText(ReactorText.ownersInstalled),
            OwnersScreen(modded: modded),
          ),
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
