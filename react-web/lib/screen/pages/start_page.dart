/// `/`, the one home: Continue with every paired server, then the Pairing
/// link field and the command that makes a link. With nothing paired the
/// pairing part is all there is. The server group stays in the nav.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';

import '../../localization/reactor_locale.dart';
import '../../localization/reactor_localizations.dart';
import '../../model/pairing_link.dart';
import '../../state/fleet_scope.dart';
import '../../ui/kit/card.dart';
import '../../ui/kit/page.dart';
import '../pairing/continue_list.dart';
import '../pairing/pair_dialog.dart';
import '../pairing/pair_link_form.dart';

class StartPage extends StatelessWidget {
  const StartPage({super.key});

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final bool paired =
        FleetScope.of(context)?.fleetManager.servers.isNotEmpty ?? false;
    final PairDialogOpener? open = PairDialogScope.of(context);
    return AppPage(
      title: reactorText(ReactorText.pageHomeTitle),
      children: <Widget>[
        if (paired) const ContinueList(),
        AppCard(
          title: reactorText(ReactorText.pairTitle),
          attributes: const <String, String>{'data-start-pair': ''},
          children: <Widget>[
            PairLinkForm(onLink: (PairingLink link) => open?.call(link)),
            const PairCommandField(),
          ],
        ),
      ],
    );
  }
}
