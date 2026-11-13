/// Map (L3): the chunk heatmap and the picked chunk.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';

import '../../localization/reactor_locale.dart';
import '../../localization/reactor_localizations.dart';
import '../../ui/kit/page.dart';
import '../heatmaps.dart';

class MapPage extends StatelessWidget {
  const MapPage({super.key});

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    return AppPage(
      title: reactorText(ReactorText.pageMapTitle),
      tip: reactorText(ReactorText.heatmapsSubtitle),
      children: const <Widget>[HeatmapsScreen()],
    );
  }
}
