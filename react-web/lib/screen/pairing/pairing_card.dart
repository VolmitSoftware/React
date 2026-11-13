/// One pairing link, ready to pair: the server's name, until when the link
/// works, what React Web may do there, this browser's name and Remember
/// this browser, then Cancel and Pair. Everything comes from the link;
/// nothing is sent before Pair. A link that cannot work says why in one
/// sentence, gives the next step and, when only a new link helps, the field
/// to paste it. The `/pair` page shows it as a card, the Pair a server
/// dialog flat.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr/component/view/check_list.dart'
    show ArcaneCheckList;
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;
import 'package:jaspr_router/jaspr_router.dart';

import '../../app/paths.dart';
import '../../localization/reactor_locale.dart';
import '../../localization/reactor_localizations.dart';
import '../../model/pairing_link.dart';
import '../../service/pairing_redeemer.dart';
import '../../service/relay_connection.dart';
import '../../state/fleet_scope.dart';
import '../../state/pair_server.dart';
import '../../ui/kit/action_row.dart';
import '../../ui/kit/alert.dart';
import '../../ui/kit/badge.dart';
import '../../ui/kit/browser.dart';
import '../../ui/kit/card.dart';
import '../../ui/kit/density.dart';
import '../../ui/kit/info_tip.dart';
import '../../ui/kit/mode.dart';
import 'pair_link_form.dart';
import 'pair_words.dart';

/// The redeemer the pairing screens use in the browser.
PairingRedeemer browserRedeemer() =>
    PairingRedeemer(openRelay: createRelayConnection, pageOrigin: pageOrigin());

class PairingCard extends StatefulWidget {
  const PairingCard({
    required this.link,
    required this.onCancel,
    this.onPaired,
    this.problem,
    this.framed = true,
    this.redeemer,
    this.now,
    super.key,
  });

  /// The link to pair; null shows [problem] and the New link field.
  final PairingLink? link;

  /// Why there is no link (the address held none).
  final PairProblem? problem;

  final void Function() onCancel;

  /// After pairing, with the entry's id; the default opens its Overview.
  final void Function(String id)? onPaired;

  /// A card of its own on the page; flat inside a dialog.
  final bool framed;
  final PairingRedeemer? redeemer;
  final DateTime Function()? now;

  @override
  State<PairingCard> createState() => _PairingCardState();
}

class _PairingCardState extends State<PairingCard> {
  static const String _nameId = 'pair-browser-name';
  static const String _rememberId = 'pair-remember';

  PairingLink? _link;
  PairProblem? _problem;
  bool _busy = false;
  bool _remember = true;
  late String _client = browserName(userAgent());

  DateTime get _now => (component.now ?? DateTime.now)().toUtc();

  @override
  void initState() {
    super.initState();
    _read(component.link, component.problem);
  }

  void _read(PairingLink? link, [PairProblem? problem]) {
    _link = link;
    _problem = problem ?? (link == null ? notALinkProblem() : null);
    if (link != null && !link.expiresAt.isAfter(_now)) {
      _problem = pairProblemFor(
        const PairingRefused(PairingFailure.expired),
        pageOrigin: pageOrigin(),
      );
    }
  }

  void _newLink(PairingLink link) => setState(() => _read(link));

  void _refused(PairingRefused refused) {
    if (!mounted) return;
    setState(
      () => _problem = pairProblemFor(refused, pageOrigin: pageOrigin()),
    );
  }

  Future<void> _pair() async {
    final PairingLink? link = _link;
    final FleetController? fleet = FleetScope.of(context);
    if (link == null || fleet == null || _busy) return;
    final RouterState? router = Router.maybeOf(context);
    setState(() {
      _busy = true;
      _problem = null;
    });
    try {
      final String id = await pairServer(
        fleet,
        link,
        redeemer: component.redeemer ?? browserRedeemer(),
        remember: _remember,
        client: _client,
      );
      ArcaneSonner.success(
        reactorText(ReactorText.pairPaired, <String, Object?>{
          'server': fleet.labelFor(id) ?? link.serverName,
        }),
      );
      final void Function(String id)? paired = component.onPaired;
      if (paired != null) {
        paired(id);
      } else {
        // The server list changes in this turn; move on after it is drawn.
        afterFrame(() => router?.push(serverPath(id)));
      }
    } on PairingRefused catch (refused) {
      _refused(refused);
    } on Object {
      _refused(const PairingRefused(PairingFailure.other));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final PairingLink? link = _link;
    final PairProblem? problem = _problem;
    final bool spent = link == null || (problem?.newLink ?? false);
    final List<Widget> body = <Widget>[
      if (!component.framed && link != null)
        dom.h3(classes: 'ui-card-title', <Widget>[
          Component.text(link.serverName),
        ]),
      // Until when the link works, while it still can; a refused link
      // says why in the alert instead.
      if (link != null && problem == null)
        dom.span(
          classes: 'ui-caption',
          attributes: const <String, String>{'data-testid': 'pair-expiry'},
          <Widget>[
            Component.text(
              reactorText(ReactorText.pairLinkUntil, <String, Object?>{
                'time': clockTime(link.expiresAt),
              }),
            ),
          ],
        ),
      if (problem != null)
        dom.div(
          key: ValueKey<String>('pair-problem:${problem.title}'),
          attributes: const <String, String>{'data-testid': 'pair-problem'},
          <Widget>[
            AppAlert(
              tone: AlertTone.error,
              title: problem.title,
              message: problem.next,
              live: true,
            ),
          ],
        ),
      if (spent)
        PairLinkForm(
          key: ValueKey<String>('new-link:${link?.code ?? ''}'),
          id: 'pair-new-link',
          label: reactorText(ReactorText.pairNewLink),
          onLink: _newLink,
        )
      else ...<Widget>[
        ArcaneFieldGroup(
          children: <Widget>[
            _abilities(link),
            ArcaneFormField(
              controlId: _nameId,
              disabled: _busy,
              children: <Widget>[
                ArcaneFieldLabel(
                  reactorText(ReactorText.pairBrowserName),
                  trailing: <Widget>[
                    InfoTip(tip: reactorText(ReactorText.pairBrowserNameTip)),
                  ],
                ),
                TextInput(
                  value: _client,
                  size: controlSize(DensityScope.of(context)),
                  fullWidth: true,
                  onChanged: (String value) => _client = value,
                ),
              ],
            ),
            ArcaneFormField(
              controlId: _rememberId,
              orientation: FieldOrientation.horizontal,
              disabled: _busy,
              children: <Widget>[
                ArcaneFieldLabel(
                  reactorText(ReactorText.pairRemember),
                  trailing: <Widget>[
                    InfoTip(tip: reactorText(ReactorText.pairRememberTip)),
                  ],
                ),
                ArcaneToggleSwitch(
                  value: _remember,
                  size: controlSize(DensityScope.of(context)),
                  onChanged: (bool value) => setState(() => _remember = value),
                ),
              ],
            ),
          ],
        ),
        ModeGate(child: _details(link)),
        FormFooter(
          cancel: RowAction(
            reactorText(ReactorText.commonCancel),
            _busy ? null : component.onCancel,
          ),
          primary: RowAction(
            reactorText(ReactorText.pairPair),
            _busy ? null : _pair,
            loading: _busy,
            attributes: const <String, String>{'data-testid': 'pair-submit'},
          ),
        ),
      ],
    ];
    if (!component.framed) return dom.div(classes: 'ui-stack', body);
    return AppCard(
      title: link?.serverName,
      attributes: const <String, String>{'data-pairing-card': ''},
      children: body,
    );
  }

  /// What the pairing lets React Web do, from the link's scopes, and the
  /// role they make up when the link names one.
  Widget _abilities(PairingLink link) => ArcaneFormField(
    children: <Widget>[
      ArcaneFieldTitle(
        reactorText(ReactorText.pairCan),
        trailing: <Widget>[
          if (roleWord(link.role) case final String role) StatusBadge(role),
        ],
      ),
      ArcaneCheckList(items: scopeWords(link.scopes), listGap: '0.375rem'),
    ],
  );

  /// The fingerprint, the ways in and the link's name, folded.
  Widget _details(PairingLink link) => ArcaneDisclosure.minimal(
    showTreeLines: false,
    summary: dom.span(classes: 'ui-summary', <Widget>[
      Component.text(reactorText(ReactorText.pairDetails)),
    ]),
    child: dom.div(classes: 'ui-stack', <Widget>[
      _fact(
        reactorText(ReactorText.pairFingerprint),
        link.fingerprint,
        tip: reactorText(ReactorText.pairFingerprintTip),
        testId: 'pair-fingerprint',
      ),
      if (link.directUrl != null)
        _fact(reactorText(ReactorText.pairDirect), link.directUrl!),
      if (link.relayUrl != null)
        _fact(reactorText(ReactorText.pairRelay), link.relayUrl!),
      if (link.label != null)
        _fact(reactorText(ReactorText.pairLinkName), link.label!),
    ]),
  );

  static Widget _fact(
    String label,
    String value, {
    String? tip,
    String? testId,
  }) => dom.div(classes: 'ui-stat', <Widget>[
    dom.span(classes: 'ui-stat-label', <Widget>[
      TipLabel(label: Component.text(label), tip: tip),
    ]),
    dom.code(
      classes: 'ui-list-cell',
      attributes: <String, String>{'dir': 'ltr', 'data-testid': ?testId},
      <Widget>[Component.text(value)],
    ),
  ]);
}
