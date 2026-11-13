/// Performance (L2): tick timing, memory and the host; internals and the
/// event bus in Advanced.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';

import '../../localization/reactor_locale.dart';
import '../../localization/reactor_localizations.dart';
import '../../ui/kit/page.dart';
import '../../ui/kit/tabs.dart';
import '../environment.dart';
import '../events.dart';
import '../internals.dart';
import '../memory.dart';
import '../monitor_parts.dart';
import '../performance.dart';

class PerformancePage extends StatelessWidget {
  const PerformancePage({super.key});

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    return AppPage(
      title: reactorText(ReactorText.performanceTitle),
      tip: reactorText(ReactorText.performanceSubtitle),
      tabs: AppTabs(
        label: reactorText(ReactorText.performanceTitle),
        tabs: <AppTab>[
          AppTab(
            'ticks',
            reactorText(ReactorText.pagePerformanceTicks),
            const PerformanceScreen(),
          ),
          AppTab(
            'memory',
            reactorText(ReactorText.memoryTitle),
            const MemoryScreen(),
          ),
          AppTab(
            'host',
            reactorText(ReactorText.pagePerformanceHost),
            const EnvironmentScreen(),
          ),
          AppTab(
            'internals',
            reactorText(ReactorText.internalsTitle),
            InternalsScreen(serverId: currentServerId(context)),
            advanced: true,
          ),
          AppTab(
            'events',
            reactorText(ReactorText.eventsTitle),
            const EventsScreen(),
            advanced: true,
          ),
        ],
      ),
    );
  }
}
