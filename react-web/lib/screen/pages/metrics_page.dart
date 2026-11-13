/// Metrics (L2, Advanced): every sampler and its stored history.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';

import '../../localization/reactor_locale.dart';
import '../../localization/reactor_localizations.dart';
import '../../ui/kit/page.dart';
import '../metrics_explorer.dart';
import '../monitor_parts.dart';

class MetricsPage extends StatelessWidget {
  const MetricsPage({super.key});

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    return AppPage(
      title: reactorText(ReactorText.metricsTitle),
      tip: reactorText(ReactorText.metricsSubtitle),
      children: <Widget>[
        MetricsExplorerScreen(
          serverId: currentServerId(context),
          metric: routeQuery(context, 'metric'),
        ),
      ],
    );
  }
}
