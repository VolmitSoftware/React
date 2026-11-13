/// What the pairing screens say: the command, what a pairing allows in plain
/// words, and for a link that cannot work one sentence on what happened and
/// one on what to do next.
library;

import 'dart:math' show max;

import '../../localization/reactor_localizations.dart';
import '../../service/pairing_redeemer.dart';

/// The command that prints a pairing link, in game or in the console.
const String pairCommand = '/react web pair';

/// One sentence on what happened, one on what to do next, and whether only
/// a new link helps.
class PairProblem {
  const PairProblem(this.title, this.next, {this.newLink = false});

  final String title;
  final String next;
  final bool newLink;
}

/// The words for a refused pairing. [pageOrigin] is this page's origin; the
/// server names it too when it refuses one.
PairProblem pairProblemFor(
  PairingRefused refused, {
  required String pageOrigin,
}) {
  PairProblem spent(ReactorText title) => PairProblem(
    reactorText(title),
    reactorText(ReactorText.pairNextNewLink),
    newLink: true,
  );
  return switch (refused.failure) {
    PairingFailure.notALink => notALinkProblem(),
    PairingFailure.used => spent(ReactorText.pairProblemUsed),
    PairingFailure.expired => spent(ReactorText.pairProblemExpired),
    PairingFailure.revoked => spent(ReactorText.pairProblemRevoked),
    PairingFailure.unknown => spent(ReactorText.pairProblemUnknown),
    PairingFailure.wrongServer => spent(ReactorText.pairProblemWrongServer),
    PairingFailure.originNotAllowed => PairProblem(
      reactorText(ReactorText.pairProblemOrigin),
      reactorText(ReactorText.pairNextOrigin, <String, Object?>{
        'origin': refused.origin ?? pageOrigin,
        'key': refused.configKey ?? 'allowedOrigins',
        'file': refused.configFile ?? 'plugins/React/web.toml',
      }),
    ),
    PairingFailure.unreachable => PairProblem(
      reactorText(ReactorText.pairProblemUnreachable),
      reactorText(ReactorText.pairNextUnreachable),
    ),
    PairingFailure.localNetworkBlocked => PairProblem(
      reactorText(ReactorText.pairProblemBlocked),
      reactorText(ReactorText.pairNextBlocked),
    ),
    PairingFailure.busy => PairProblem(
      reactorText(ReactorText.pairProblemBusy),
      reactorText(ReactorText.pairNextBusy),
    ),
    PairingFailure.other => PairProblem(
      reactorText(ReactorText.pairProblemOther),
      reactorText(ReactorText.pairNextOther),
    ),
  };
}

/// Pasted or opened text that holds no pairing link.
PairProblem notALinkProblem() => PairProblem(
  reactorText(ReactorText.pairProblemNotALink),
  reactorText(ReactorText.pairNextNotALink),
  newLink: true,
);

/// The server revoked or expired a pairing after it was made.
PairProblem pairingEndedProblem() => PairProblem(
  reactorText(ReactorText.pairEnded),
  reactorText(ReactorText.pairNextNewLink),
  newLink: true,
);

/// What a pairing with [scopes] lets React Web do, in the server's order.
List<String> scopeWords(List<String> scopes) => <String>[
  for (final (String scope, ReactorText words) in const <(String, ReactorText)>[
    ('read', ReactorText.pairScopeRead),
    ('op:execute', ReactorText.pairScopeOperate),
    ('admin', ReactorText.pairScopeAdmin),
    ('console:read', ReactorText.pairScopeConsoleRead),
    ('console:execute', ReactorText.pairScopeConsoleRun),
  ])
    if (scopes.contains(scope)) reactorText(words),
];

/// The role a link or token names, as the page headers show it; null for
/// a link made from scopes.
String? roleWord(String? role) => switch (role) {
  'admin' => reactorText(ReactorText.roleAdmin),
  'operator' => reactorText(ReactorText.roleOperator),
  'viewer' => reactorText(ReactorText.roleViewer),
  _ => null,
};

/// "14:05" in local time.
String clockTime(DateTime at) {
  final DateTime local = at.toLocal();
  return '${local.hour.toString().padLeft(2, '0')}:'
      '${local.minute.toString().padLeft(2, '0')}';
}

/// "Pairing ends in n days unless used." when the pairing ends within three
/// days of [now], to the nearest day, or in hours under a day; null
/// otherwise or when the end is unknown.
String? pairingEndsSoon(DateTime? expiresAt, DateTime now) {
  if (expiresAt == null) return null;
  final Duration left = expiresAt.difference(now);
  if (left.isNegative || left > const Duration(days: 3)) return null;
  if (left < const Duration(days: 1)) {
    final int hours = max(1, (left.inMinutes / 60).round());
    return reactorText(ReactorText.pairEndsInHours, <String, Object?>{
      'hours': hours,
    });
  }
  final int days = (left.inMinutes / Duration.minutesPerDay).round();
  return days == 1
      ? reactorText(ReactorText.pairEndsTomorrow)
      : reactorText(ReactorText.pairEndsIn, <String, Object?>{'days': days});
}
