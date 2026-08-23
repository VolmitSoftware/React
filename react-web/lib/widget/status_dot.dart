library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../state/connection_manager.dart';
import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../ui/reactor_ui.dart';

class StatusDot extends StatelessWidget {
  final ConnState state;
  final bool showLabel;

  const StatusDot({required this.state, this.showLabel = false, super.key});

  static String labelFor(ConnState state) => switch (state) {
    ConnState.live => reactorText(ReactorText.statusLive),
    ConnState.connecting => reactorText(ReactorText.statusConnecting),
    ConnState.degraded => reactorText(ReactorText.statusDegraded),
    ConnState.offline => reactorText(ReactorText.statusOffline),
  };

  static ReactorStatus statusFor(ConnState state) => switch (state) {
    ConnState.live => ReactorStatus.healthy,
    ConnState.connecting => ReactorStatus.info,
    ConnState.degraded => ReactorStatus.warning,
    ConnState.offline => ReactorStatus.critical,
  };

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final ReactorStatus status = statusFor(state);
    final Widget dot = reactorStatusDot(status, label: labelFor(state));
    if (!showLabel) return dot;
    return dom.div(
      styles: const dom.Styles(
        raw: <String, String>{
          'display': 'inline-flex',
          'align-items': 'center',
          'gap': '0.45rem',
        },
      ),
      <Widget>[
        dot,
        dom.span(
          styles: dom.Styles(
            raw: <String, String>{
              'font-size': '0.75rem',
              'font-weight': '500',
              'color': reactorStatusColor(status),
            },
          ),
          <Widget>[Component.text(labelFor(state))],
        ),
      ],
    );
  }
}
