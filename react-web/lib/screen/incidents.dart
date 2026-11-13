/// Incidents: the server's incident state and primary cause, the scheduler
/// pressure tiles, the latest incidents and, in Advanced, what feeds the
/// score. The incident list is polled over REST (the plugin has no incident
/// socket); the tiles read the live snapshot.
library;

import 'dart:async';
import 'dart:convert';
import 'dart:math' as math;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:arcane_jaspr/component/view/spec_row.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component, kIsWeb;
import 'package:universal_web/web.dart' as web;

import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/alert_thresholds.dart';
import '../model/health.dart';
import '../model/incident_status.dart';
import '../model/sampler_sample.dart';
import '../model/server_snapshot.dart';
import '../service/react_client.dart';
import '../service/react_exceptions.dart';
import '../state/fleet_scope.dart';
import '../state/server_scope.dart';
import '../state/server_session_scope.dart';
import '../ui/kit/action_row.dart';
import '../ui/kit/alert.dart';
import '../ui/kit/badge.dart';
import '../ui/kit/card.dart';
import '../ui/kit/health_badge.dart';
import '../ui/kit/metric_tile.dart';
import '../ui/kit/mode.dart';
import '../ui/kit/page.dart';
import '../ui/kit/section.dart';
import '../ui/kit/states.dart';
import '../ui/error_text.dart';
import '../widget/server_snapshot_state.dart';
import 'monitor_parts.dart';

/// How often the incident list is fetched.
const Duration incidentsRefreshInterval = Duration(seconds: 5);

/// A list older than this says when it was last refreshed.
const Duration incidentsStaleAfter = Duration(seconds: 15);

/// The score's level, from the shared verdict ([HealthMetric.incidentScore])
/// so the Overview, the server group and this page read it alike.
HealthLevel incidentScoreLevel(double score, AlertThresholds thresholds) =>
    metricHealth(HealthMetric.incidentScore, score, thresholds);

/// The level in the app's one health vocabulary: Healthy, Warning,
/// Critical.
String _scoreLevel(HealthLevel level) =>
    levelWord(level) ?? reactorText(ReactorText.commonHealthy);

/// The plugin's incident state (NORMAL, ELEVATED, CRITICAL, ...) in words.
String incidentStateLabel(String state) {
  final String upper = state.trim().toUpperCase();
  if (upper.contains('PANIC')) return reactorText(ReactorText.pressurePanic);
  if (upper.contains('PRESSURE')) {
    return reactorText(ReactorText.pressurePressure);
  }
  if (upper.contains('CRIT')) return reactorText(ReactorText.statusCritical);
  if (upper.contains('ACTIVE')) return reactorText(ReactorText.commonActive);
  if (upper.contains('ELEV')) return reactorText(ReactorText.statusElevated);
  if (upper.contains('WARN')) return reactorText(ReactorText.commonWarning);
  if (upper.contains('NORMAL')) return reactorText(ReactorText.pressureNormal);
  if (upper.contains('DISABLED')) {
    return reactorText(ReactorText.commonDisabled);
  }
  return _titleCase(state);
}

(String, BadgeTone) _severity(String severity) {
  final String upper = severity.toUpperCase();
  if (upper.contains('CRIT') || upper.contains('ERROR')) {
    return (reactorText(ReactorText.statusCritical), BadgeTone.error);
  }
  if (upper.contains('WARN')) {
    return (reactorText(ReactorText.commonWarning), BadgeTone.warning);
  }
  if (upper.contains('INFO')) return (_titleCase(severity), BadgeTone.info);
  return (_titleCase(severity), BadgeTone.neutral);
}

String _titleCase(String raw) {
  final String words = raw.trim().replaceAll('_', ' ').toLowerCase();
  if (words.isEmpty) return raw;
  return words[0].toUpperCase() + words.substring(1);
}

/// The full date and time, for a title beside a relative time.
String _stamp(int epochMs) {
  if (epochMs <= 0) return reactorText(ReactorText.commonUnavailable);
  final DateTime time = DateTime.fromMillisecondsSinceEpoch(epochMs).toLocal();
  String two(int value) => value.toString().padLeft(2, '0');
  return '${time.year}-${two(time.month)}-${two(time.day)} '
      '${two(time.hour)}:${two(time.minute)}:${two(time.second)}';
}

String _ago(Duration age) {
  final int seconds = math.max(0, age.inSeconds);
  if (seconds < 60) {
    return reactorText(ReactorText.commonSecondsAgo, <String, Object?>{
      'value': seconds,
    });
  }
  if (seconds < 3600) {
    return reactorText(ReactorText.commonMinutesAgo, <String, Object?>{
      'value': seconds ~/ 60,
    });
  }
  if (seconds < 86400) {
    return reactorText(ReactorText.commonHoursAgo, <String, Object?>{
      'value': seconds ~/ 3600,
    });
  }
  return reactorText(ReactorText.commonDaysAgo, <String, Object?>{
    'value': seconds ~/ 86400,
  });
}

/// A failure a retry cannot fix (no access, no such endpoint, a revoked or
/// foreign token): shown without Retry.
bool _lasting(Object error) => switch (reactErrorKind(error)) {
  ReactErrorKind.forbidden ||
  ReactErrorKind.notFound ||
  ReactErrorKind.revoked ||
  ReactErrorKind.wrongServer => true,
  _ => false,
};

/// Polls the incident list for the server in scope and shows it.
class IncidentsScreen extends StatefulWidget {
  const IncidentsScreen({super.key});

  @override
  State<IncidentsScreen> createState() => _IncidentsScreenState();
}

class _IncidentsScreenState extends State<IncidentsScreen> {
  IIncidentClient? _client;
  IncidentStatus? _status;
  Object? _error;
  DateTime? _updatedAt;
  bool _loading = false;
  Timer? _timer;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final IIncidentClient? client = ServerSessionScope.of(context)
        ?.clientAs<IIncidentClient>();
    if (identical(client, _client)) return;
    _timer?.cancel();
    _timer = null;
    _client = client;
    _status = null;
    _error = null;
    _updatedAt = null;
    _loading = false;
    if (client == null) return;
    unawaited(_load(client));
    _timer = Timer.periodic(incidentsRefreshInterval, (Timer _) {
      // A request that hangs still lets the list say how old it is.
      if (_loading) {
        setState(() {});
        return;
      }
      unawaited(_load(client));
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  Future<void> _load(IIncidentClient client) async {
    if (_loading || !identical(client, _client)) return;
    _loading = true;
    try {
      final IncidentStatus status = await client.incidents();
      if (!mounted || !identical(client, _client)) return;
      setState(() {
        _status = status;
        _error = null;
        _updatedAt = DateTime.now();
      });
    } on Object catch (error) {
      if (!mounted || !identical(client, _client)) return;
      setState(() => _error = error);
    } finally {
      if (identical(client, _client)) _loading = false;
    }
  }

  void _retry() {
    final IIncidentClient? client = _client;
    if (client == null || _loading) return;
    if (_status == null) setState(() => _error = null);
    unawaited(_load(client));
  }

  @override
  Widget build(BuildContext context) => IncidentsView(
    status: _status,
    error: _error,
    updatedAt: _updatedAt,
    live: _client != null,
    onRetry: _retry,
  );
}

/// The Incidents page body for a given incident answer; the tiles and the
/// backlog chart read [ServerScope]'s snapshot.
class IncidentsView extends StatelessWidget {
  const IncidentsView({
    this.status,
    this.error,
    this.updatedAt,
    this.live = true,
    this.onRetry,
    this.now,
    super.key,
  });

  /// The last incident answer; null until the first one.
  final IncidentStatus? status;

  /// The last request's failure; the list stays when there is one.
  final Object? error;

  /// When [status] arrived.
  final DateTime? updatedAt;

  /// Whether the server has an incident client (a live connection).
  final bool live;
  final void Function()? onRetry;

  /// The clock for ages and dates; the wall clock when null.
  final DateTime? now;

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final ServerScope? scope = ServerScope.of(context);
    final ServerSnapshot? snapshot = scope?.snapshot;
    final DateTime clock = now ?? DateTime.now();
    final IncidentStatus? status = this.status;
    final AlertThresholds thresholds =
        FleetScope.of(context)?.alertStore.thresholds ??
        AlertThresholds.defaults;
    final double? score = _score(snapshot);
    return PageStack(
      children: <Widget>[
        if (live && status != null) _statusLine(status, score, thresholds),
        if (snapshot == null)
          serverSnapshotState(scope: scope, icon: ArcaneIcon.siren())
        else
          MetricGrid(children: _tiles(snapshot, score, thresholds)),
        _latest(clock),
        ModeGate(child: _contributors()),
      ],
    );
  }

  SamplerSample? _available(ServerSnapshot snapshot, String id) {
    final SamplerSample? sample = snapshot.sampler(id);
    return sample != null && sample.available ? sample : null;
  }

  /// The live score, else the last polled one.
  double? _score(ServerSnapshot? snapshot) {
    final SamplerSample? live = snapshot == null
        ? null
        : _available(snapshot, 'incident-score');
    final IncidentStatus? polled = status;
    return live?.value ??
        (polled != null && polled.scoreAvailable ? polled.score : null);
  }

  List<Widget> _tiles(
    ServerSnapshot snapshot,
    double? score,
    AlertThresholds thresholds,
  ) {
    final SamplerSample? live = _available(snapshot, 'incident-score');
    final SamplerSample? backlog = _available(snapshot, 'scheduler-backlog');
    final SamplerSample? growth = _available(snapshot, 'backlog-growth-rate');
    final HealthLevel? level = score == null
        ? null
        : incidentScoreLevel(score, thresholds);
    return <Widget>[
      MetricTile(
        label: reactorText(ReactorText.incidentsScore),
        value: live?.display ?? score?.toStringAsFixed(1) ?? '--',
        // The status line above says the level; the tile only colours it.
        tone: level == null ? BadgeTone.neutral : levelTone(level),
        history: live?.history ?? const <double>[],
      ),
      _samplerTile(ReactorText.incidentsBacklog, backlog),
      _samplerTile(ReactorText.incidentsBacklogGrowth, growth),
    ];
  }

  Widget _samplerTile(ReactorText label, SamplerSample? sample) =>
      samplerTile(reactorText(label), sample);

  /// The incident level (the same as the score tile's) and its primary
  /// cause. The plugin's own state is added when it is not Normal (its
  /// incident mode is on).
  Widget _statusLine(
    IncidentStatus status,
    double? score,
    AlertThresholds thresholds,
  ) {
    final IncidentContributor? primary = status.primaryContributor;
    final String cause = primary != null && primary.scorePoints > 0
        ? reactorText(ReactorText.incidentsPrimaryCause, <String, Object?>{
            'metric': primary.label,
            'value': primary.display,
            'points': primary.scorePoints.toStringAsFixed(1),
          })
        : reactorText(
            score != null
                ? ReactorText.incidentsNoDominantCause
                : ReactorText.incidentsScoreUnavailable,
          );
    final HealthLevel level = score == null
        ? HealthLevel.unknown
        : incidentScoreLevel(score, thresholds);
    final String state = status.state.trim().toUpperCase();
    final bool modeOn = state.contains('ACTIVE');
    return AppAlert(
      tone: switch (level) {
        HealthLevel.critical => AlertTone.error,
        HealthLevel.warning => AlertTone.warning,
        HealthLevel.healthy => AlertTone.ok,
        HealthLevel.unknown => AlertTone.info,
      },
      title: level == HealthLevel.unknown
          ? incidentStateLabel(status.state)
          : _scoreLevel(level),
      message: modeOn ? '$cause · ${incidentStateLabel(status.state)}' : cause,
    );
  }

  Widget _latest(DateTime clock) {
    final IncidentStatus? status = this.status;
    final Object? error = this.error;
    final List<Widget> body;
    if (!live) {
      body = <Widget>[
        EmptyState(
          message: reactorText(ReactorText.incidentsLiveRequired),
          icon: ArcaneIcon.plugZap(),
        ),
      ];
    } else if (status == null && error != null) {
      final bool forbidden = reactErrorKind(error) == ReactErrorKind.forbidden;
      body = <Widget>[
        ErrorState(
          title: forbidden
              ? null
              : reactorText(ReactorText.incidentsRequestFailed),
          message: localizedReactorError(error),
          onRetry: _lasting(error) ? null : onRetry,
        ),
      ];
    } else if (status == null) {
      body = <Widget>[
        LoadingState(label: reactorText(ReactorText.incidentsLoading)),
      ];
    } else {
      final DateTime? updatedAt = this.updatedAt;
      final bool stale =
          error != null ||
          (updatedAt != null &&
              clock.difference(updatedAt) > incidentsStaleAfter);
      body = <Widget>[
        if (stale) _staleNote(clock, error),
        if (status.incidents.isEmpty)
          EmptyState(
            message: reactorText(ReactorText.incidentsNoEvents),
            icon: ArcaneIcon.shieldCheck(),
          )
        else
          _IncidentList(incidents: status.incidents, now: clock),
      ];
    }
    return AppCard(
      title: reactorText(ReactorText.incidentsLatest),
      children: body,
    );
  }

  Widget _staleNote(DateTime clock, Object? error) {
    final DateTime? updatedAt = this.updatedAt;
    final String age = updatedAt == null
        ? reactorText(ReactorText.commonNever)
        : _ago(clock.difference(updatedAt));
    return AppAlert(
      tone: AlertTone.warning,
      title: error == null
          ? null
          : reactorText(ReactorText.incidentsRefreshFailed),
      message: reactorText(ReactorText.incidentsUpdated, <String, Object?>{
        'time': age,
      }),
      action: onRetry == null
          ? null
          : ActionRow.footer(
              dense: true,
              secondary: <RowAction>[
                RowAction(reactorText(ReactorText.commonRetry), onRetry),
              ],
            ),
    );
  }

  Widget _contributors() {
    final IncidentStatus? status = this.status;
    if (status == null) return const Component.fragment(<Widget>[]);
    final List<IncidentContributor> contributors =
        List<IncidentContributor>.of(status.contributors)..sort((
          IncidentContributor left,
          IncidentContributor right,
        ) {
          if (left.available != right.available) return left.available ? -1 : 1;
          return right.scorePoints.compareTo(left.scorePoints);
        });
    // On the page, as the tiles above: one tile style per page.
    return PageSection(
      title: reactorText(ReactorText.incidentsContributors),
      children: <Widget>[
        if (contributors.isEmpty)
          dom.p(classes: 'ui-caption', <Widget>[
            Component.text(reactorText(ReactorText.incidentsNoFactors)),
          ])
        else
          MetricGrid(
            children: <Widget>[
              for (final IncidentContributor contributor in contributors)
                _contributorTile(contributor),
            ],
          ),
      ],
    );
  }

  Widget _contributorTile(IncidentContributor contributor) {
    if (!contributor.available) {
      return MetricTile(
        label: contributor.label,
        value: '--',
        caption: reactorText(ReactorText.commonUnavailable),
      );
    }
    final double pressure = contributor.pressure.clamp(0.0, 1.0);
    return MetricTile(
      label: contributor.label,
      value: contributor.display,
      // Points are a figure, not a state: a muted line; the meter's colour
      // carries the pressure.
      note:
          '${contributor.scorePoints.toStringAsFixed(1)} '
          '${reactorText(ReactorText.incidentsScorePoints)}',
      tone: pressure >= 0.7
          ? BadgeTone.error
          : pressure >= 0.4
          ? BadgeTone.warning
          : BadgeTone.ok,
      meter: pressure,
    );
  }
}

/// The incidents as shadcn accordion rows, newest first. The rows are built
/// again only when the incidents, the mode or the language change, and a
/// rebuild reopens the rows that were open, so polling never folds a row
/// someone is reading.
class _IncidentList extends StatefulWidget {
  const _IncidentList({required this.incidents, required this.now});

  final List<IncidentRecord> incidents;
  final DateTime now;

  @override
  State<_IncidentList> createState() => _IncidentListState();
}

class _IncidentListState extends State<_IncidentList> {
  static const String _listId = 'reactor-incident-list';

  String? _key;
  Widget? _rows;

  /// The incidents whose rows are open on the page now.
  Set<String> _openIds() {
    if (!kIsWeb) return const <String>{};
    final web.NodeList found = web.document.querySelectorAll(
      '#$_listId details[open] [data-incident]',
    );
    return <String>{
      for (int index = 0; index < found.length; index++)
        ?(found.item(index) as web.Element?)?.getAttribute('data-incident'),
    };
  }

  @override
  Widget build(BuildContext context) {
    final ReactorLocaleScope? locale = dependOnReactorLocale(context);
    final bool advanced = ModeScope.advanced(context);
    final DateTime now = component.now;
    final String key = <Object?>[
      identityHashCode(locale),
      advanced,
      '${now.year}-${now.month}-${now.day}',
      jsonEncode(<Object?>[
        for (final IncidentRecord incident in component.incidents)
          incident.toJson(),
      ]),
    ].join('|');
    if (key != _key || _rows == null) {
      _key = key;
      _rows = _build(advanced, now);
    }
    return _rows!;
  }

  Widget _build(bool advanced, DateTime now) {
    final Set<String> open = _openIds();
    final List<IncidentRecord> incidents =
        List<IncidentRecord>.of(component.incidents)
          ..sort((IncidentRecord left, IncidentRecord right) {
            final bool leftOver = _resolved(left);
            if (leftOver != _resolved(right)) return leftOver ? 1 : -1;
            return right.occurredAtMs.compareTo(left.occurredAtMs);
          });
    return dom.div(id: _listId, <Widget>[
      ArcaneAccordion(
        allowMultiple: true,
        items: <ArcaneAccordionItem>[
          for (final IncidentRecord incident in incidents)
            ArcaneAccordionItem(
              title: incident.title,
              customTitle: _summary(incident, now),
              customContent: _detail(incident, advanced),
              defaultOpen: open.contains(incident.id),
            ),
        ],
      ),
    ]);
  }

  /// The state badge (Resolved once it is over) and how long ago, then the
  /// title on its own line, then the cause.
  Widget _summary(IncidentRecord incident, DateTime now) {
    final (String severity, BadgeTone tone) = _resolved(incident)
        ? (reactorText(ReactorText.incidentsResolved), BadgeTone.neutral)
        : _severity(incident.severity);
    return dom.div(
      attributes: <String, String>{'data-incident': incident.id},
      <Widget>[
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          spacing: 4,
          children: <Widget>[
            Wrap(
              spacing: 8,
              runSpacing: 4,
              crossAxisAlignment: WrapCrossAlignment.center,
              children: <Widget>[
                StatusBadge(severity, tone: tone),
                dom.span(
                  classes: 'ui-caption',
                  attributes: <String, String>{
                    'title': _stamp(incident.occurredAtMs),
                  },
                  <Widget>[
                    Component.text(
                      incident.occurredAtMs <= 0
                          ? reactorText(ReactorText.commonUnavailable)
                          : _ago(
                              now.difference(
                                DateTime.fromMillisecondsSinceEpoch(
                                  incident.occurredAtMs,
                                ),
                              ),
                            ),
                    ),
                  ],
                ),
              ],
            ),
            dom.span(<Widget>[Component.text(incident.title)]),
            if (incident.cause.isNotEmpty)
              dom.span(classes: 'ui-caption', <Widget>[
                Component.text(incident.cause),
              ]),
          ],
        ),
      ],
    );
  }

  static bool _resolved(IncidentRecord incident) =>
      incident.phase.toUpperCase().contains('RESOLV');

  Widget _detail(IncidentRecord incident, bool advanced) {
    final IncidentLocation? location = incident.location;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      spacing: 8,
      children: <Widget>[
        if (incident.summary.isNotEmpty)
          dom.span(<Widget>[Component.text(incident.summary)]),
        if (location != null)
          ArcaneSpecRow(
            label: reactorText(ReactorText.incidentsLocation),
            value:
                '${location.world} · ${location.x}, ${location.y}, ${location.z}',
          ),
        if (incident.actions.isNotEmpty)
          ..._group(reactorText(ReactorText.incidentsActions), <Widget>[
            for (final IncidentAction action in incident.actions)
              ArcaneSpecRow(
                label: action.label,
                value: action.detail.isEmpty
                    ? _titleCase(action.status)
                    : '${_titleCase(action.status)} · ${action.detail}',
              ),
          ]),
        if (advanced) ...<Widget>[
          dom.span(classes: 'ui-caption', <Widget>[
            Component.text(
              <String>[
                _titleCase(incident.phase),
                incident.source,
              ].where((String part) => part.isNotEmpty).join(' · '),
            ),
          ]),
          if (incident.evidence.isNotEmpty)
            ..._group(reactorText(ReactorText.incidentsEvidence), <Widget>[
              for (final IncidentContributor evidence in incident.evidence)
                ArcaneSpecRow(
                  label: evidence.label,
                  value: evidence.available
                      ? evidence.display
                      : reactorText(ReactorText.commonUnavailable),
                ),
            ]),
          if (incident.context.isNotEmpty)
            ..._group(reactorText(ReactorText.incidentsDetails), <Widget>[
              for (final MapEntry<String, String> entry
                  in incident.context.entries)
                ArcaneSpecRow(label: entry.key, value: entry.value),
            ]),
        ],
      ],
    );
  }

  List<Widget> _group(String heading, List<Widget> rows) => <Widget>[
    dom.strong(<Widget>[Component.text(heading)]),
    ...rows,
  ];
}
