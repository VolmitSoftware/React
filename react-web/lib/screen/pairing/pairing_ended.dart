/// "Pairing ended." on a server's pages: the server revoked or expired the
/// pairing, or it ended with the tab it was kept for. The page shows no data
/// then, so the notice says since when there is none. Only a new link
/// helps, so the field for one comes with it.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../../localization/reactor_locale.dart';
import '../../localization/reactor_localizations.dart';
import '../../model/pairing_link.dart';
import '../../ui/kit/alert.dart';
import '../../ui/kit/card.dart';
import 'pair_dialog.dart';
import 'pair_link_form.dart';
import 'pair_words.dart';

class PairingEndedNotice extends StatelessWidget {
  const PairingEndedNotice({required this.serverId, this.since, super.key});

  final String serverId;

  /// When the last data came; null when none ever did.
  final DateTime? since;

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final PairProblem ended = pairingEndedProblem();
    final PairDialogOpener? open = PairDialogScope.of(context);
    return AppCard(
      attributes: const <String, String>{'data-pairing-ended': ''},
      children: <Widget>[
        AppAlert(
          tone: AlertTone.error,
          title: ended.title,
          message: ended.next,
          live: true,
        ),
        if (since case final DateTime at)
          dom.p(classes: 'ui-caption', <Widget>[
            Component.text(
              reactorText(ReactorText.pairNoDataSince, <String, Object?>{
                'time': clockTime(at),
              }),
            ),
          ]),
        PairLinkForm(
          id: 'new-link-$serverId',
          label: reactorText(ReactorText.pairNewLink),
          onLink: (PairingLink link) => open?.call(link),
        ),
      ],
    );
  }
}
