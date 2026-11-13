/// `/pair#RCT3.…`: where a pairing link lands. The code moves out of the
/// address bar into memory as the page opens (and again when only the
/// fragment changes), so it stays out of the history; then one pairing
/// card.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr_router/jaspr_router.dart';

import '../../app/paths.dart';
import '../../localization/reactor_locale.dart';
import '../../localization/reactor_localizations.dart';
import '../../model/pairing_link.dart';
import '../../ui/kit/browser.dart';
import '../../ui/kit/page.dart';
import '../pairing/pairing_card.dart';

class PairPage extends StatefulWidget {
  const PairPage({super.key});

  @override
  State<PairPage> createState() => _PairPageState();
}

class _PairPageState extends State<PairPage> {
  /// The last link this tab opened, kept past the page: the shell builds
  /// the page again when the window crosses the phone width (a turned
  /// phone), and the address no longer holds the code by then. Pair and
  /// Cancel let it go.
  static String _opened = '';

  /// Read as the page opens; the address no longer holds it.
  late PairingLink? _link = _read(takeFragment());

  static PairingLink? _read(String fragment) {
    if (fragment.isNotEmpty) _opened = fragment;
    return PairingLink.parse(_opened);
  }

  late final void Function() _stopWatching;

  @override
  void initState() {
    super.initState();
    _stopWatching = watchFragment((String fragment) {
      if (!mounted) return;
      setState(() => _link = _read(fragment));
    });
  }

  @override
  void dispose() {
    _stopWatching();
    super.dispose();
  }

  void _cancel() {
    _opened = '';
    Router.maybeOf(context)?.push(kRouteRoot);
  }

  void _paired(String id) {
    _opened = '';
    final RouterState? router = Router.maybeOf(context);
    afterFrame(() => router?.push(serverPath(id)));
  }

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    return AppPage(
      title: reactorText(ReactorText.pairTitle),
      children: <Widget>[
        // One compact card, as a dialog would hold it.
        dom.div(classes: 'reactor-narrow', <Widget>[
          PairingCard(
            // The card starts over for each new link.
            key: ValueKey<String>(_link?.code ?? ''),
            link: _link,
            onCancel: _cancel,
            onPaired: _paired,
          ),
        ]),
      ],
    );
  }
}
