/// Tuning (L4): features (with a Governors filter), tweaks, world budgets
/// and the config file.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';

import '../../localization/reactor_locale.dart';
import '../../localization/reactor_localizations.dart';
import '../../ui/kit/page.dart';
import '../../ui/kit/tabs.dart';
import '../config_editor.dart';
import '../optimization.dart';
import '../world_overrides.dart';
import '../worlds.dart';

class TuningPage extends StatelessWidget {
  const TuningPage({super.key});

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    return AppPage(
      title: reactorText(ReactorText.pageTuningTitle),
      tip: reactorText(ReactorText.pageTuningTip),
      tabs: AppTabs(
        label: reactorText(ReactorText.pageTuningTitle),
        tabs: <AppTab>[
          AppTab(
            'features',
            reactorText(ReactorText.pageTuningFeatures),
            const ControlListScreen.features(),
          ),
          AppTab(
            'tweaks',
            reactorText(ReactorText.tweaksTitle),
            const ControlListScreen.tweaks(),
          ),
          AppTab(
            'worlds',
            reactorText(ReactorText.worldsTitle),
            const PageStack(
              children: <Widget>[WorldsScreen(), WorldOverridesScreen()],
            ),
          ),
          AppTab(
            'config',
            reactorText(ReactorText.pageTuningConfig),
            const ConfigEditorScreen(),
          ),
        ],
      ),
    );
  }
}
