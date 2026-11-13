/// Help beside a label: a square (i) button named "More info" and described
/// by the tip. Hover or focus shows the tip after a short rest; a click or
/// tap pins it open until Escape, a second click or a click elsewhere. On
/// shadcn's hover card. Explanations live here, never in paragraphs.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component, kIsWeb;
import 'package:universal_web/js_interop.dart';
import 'package:universal_web/web.dart' as web;

import '../../localization/reactor_locale.dart';
import '../../localization/reactor_localizations.dart';

int _nextTipId = 0;

/// How long a resting pointer waits before the tip opens.
const int tipOpenDelayMs = 300;

class InfoTip extends StatefulWidget {
  const InfoTip({required this.tip, super.key});

  /// One or two short sentences.
  final String tip;

  @override
  State<InfoTip> createState() => _InfoTipState();
}

class _InfoTipState extends State<InfoTip> {
  final String _id = 'ui-tip-${_nextTipId++}';
  bool _open = false;

  /// Opened by a click or tap: stays until dismissed, not until the
  /// pointer leaves.
  bool _pinned = false;
  JSFunction? _escape;

  void _changed(bool open) {
    if (!open && _pinned && kIsWeb) {
      // The hover card closes on leave and blur; a pinned tip waits for an
      // explicit close (Escape and outside clicks clear the pin first).
      final web.Element? active = web.document.activeElement;
      final bool inside =
          active != null &&
          (web.document.getElementById('$_id-wrap')?.contains(active) ?? false);
      if (inside) return;
    }
    if (!open && _pinned) {
      _pinned = false;
      _listen(false);
    }
    if (mounted && open != _open) setState(() => _open = open);
  }

  void _click() {
    setState(() {
      _pinned = !_pinned;
      _open = _pinned;
    });
    _listen(_pinned);
  }

  /// Escape while pinned. The hover card swallows Escape on its trigger and
  /// asks to close, which [_changed] ignores while pinned, so the key is
  /// caught on the window first.
  void _listen(bool on) {
    if (!kIsWeb) return;
    final JSFunction? current = _escape;
    if (current != null) {
      web.window.removeEventListener(
        'keydown',
        current,
        web.EventListenerOptions(capture: true),
      );
      _escape = null;
    }
    if (!on) return;
    final JSFunction escape = ((web.KeyboardEvent event) {
      if (event.key != 'Escape' || !_pinned) return;
      event.preventDefault();
      event.stopPropagation();
      _pinned = false;
      _listen(false);
      if (mounted) setState(() => _open = false);
    }).toJS;
    _escape = escape;
    web.window.addEventListener(
      'keydown',
      escape,
      web.AddEventListenerOptions(capture: true),
    );
  }

  @override
  void dispose() {
    _listen(false);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    return dom.span(
      id: '$_id-wrap',
      classes: 'ui-info${_open ? ' is-open' : ''}',
      <Widget>[
        ArcaneHoverCard(
          id: _id,
          isOpen: _open,
          onOpenChange: _changed,
          openDelay: tipOpenDelayMs,
          closeDelay: 150,
          position: FloatingPosition.bottomStart,
          showArrow: false,
          offset: 6,
          maxWidth: 288,
          trigger: Button.ghost(
            icon: ArcaneIcon.info(size: IconSize.sm),
            size: ButtonSize.iconSm,
            attributes: <String, String>{
              'data-info-tip': '',
              'aria-label': reactorText(ReactorText.commonMoreInfo),
              'aria-describedby': '$_id-text',
              'aria-expanded': '$_open',
            },
            onPressed: _click,
          ),
          content: dom.span(id: '$_id-text', classes: 'ui-info-text', <Widget>[
            Component.text(component.tip),
          ]),
        ),
      ],
    );
  }
}

/// [label] followed by its (i) button, or [label] alone without a tip.
class TipLabel extends StatelessWidget {
  const TipLabel({required this.label, this.tip, super.key});

  final Widget label;
  final String? tip;

  @override
  Widget build(BuildContext context) {
    final String? tip = this.tip;
    if (tip == null || tip.trim().isEmpty) return label;
    return dom.span(classes: 'ui-tip-label', <Widget>[
      label,
      InfoTip(tip: tip),
    ]);
  }
}
