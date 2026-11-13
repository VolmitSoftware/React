/// Activity (L2): entities and players, chunks, game mechanics.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';

import '../../localization/reactor_locale.dart';
import '../../localization/reactor_localizations.dart';
import '../../ui/kit/page.dart';
import '../../ui/kit/tabs.dart';
import '../chunks.dart';
import '../entities.dart';
import '../mechanics.dart';

class ActivityPage extends StatelessWidget {
  const ActivityPage({super.key});

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    return AppPage(
      title: reactorText(ReactorText.pageActivityTitle),
      tip: reactorText(ReactorText.pageActivityTip),
      tabs: AppTabs(
        label: reactorText(ReactorText.pageActivityTitle),
        tabs: <AppTab>[
          AppTab(
            'entities',
            reactorText(ReactorText.entitiesTitle),
            const EntitiesScreen(),
          ),
          AppTab(
            'chunks',
            reactorText(ReactorText.chunksTitle),
            const ChunksScreen(),
          ),
          AppTab(
            'mechanics',
            reactorText(ReactorText.mechanicsTitle),
            const MechanicsScreen(),
          ),
        ],
      ),
    );
  }
}
