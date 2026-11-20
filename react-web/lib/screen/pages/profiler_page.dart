/// Profiler (L3): the live sampling profiler and the saved snapshots.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';

import '../../localization/reactor_locale.dart';
import '../../localization/reactor_localizations.dart';
import '../../ui/kit/page.dart';
import '../../ui/kit/tabs.dart';
import '../profiler.dart';
import '../snapshots.dart';

class ProfilerPage extends StatelessWidget {
  const ProfilerPage({super.key});

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    return AppPage(
      title: reactorText(ReactorText.pageProfilerTitle),
      tip: reactorText(ReactorText.pageProfilerTip),
      tabs: AppTabs(
        label: reactorText(ReactorText.pageProfilerTitle),
        tabs: <AppTab>[
          AppTab(
            'live',
            reactorText(ReactorText.profilerLive),
            const ProfilerScreen(),
          ),
          AppTab(
            'snapshots',
            reactorText(ReactorText.profilerSnapshots),
            const SnapshotsScreen(),
          ),
        ],
      ),
    );
  }
}
