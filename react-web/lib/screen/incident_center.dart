library;

import 'dart:async';

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/incident_status.dart';
import '../model/sampler_sample.dart';
import '../service/react_client.dart';
import '../state/connection_manager.dart';
import '../state/operate_scope.dart';
import '../state/server_scope.dart';
import '../ui/reactor_ui.dart';
import '../widget/section_card.dart' show statGrid;

const Duration _refreshInterval = Duration(seconds: 5);

Widget _stateChip(String? state) {
  if (state == null) {
    return reactorBadge(
      reactorText(ReactorText.statusOffline),
      ReactorStatus.neutral,
    );
  }
  final String upper = state.toUpperCase();
  final String label = _localizedIncidentState(state);
  if (upper.contains('CRIT') || upper.contains('PANIC')) {
    return reactorBadge(label, ReactorStatus.critical);
  }
  if (upper.contains('ACTIVE') ||
      upper.contains('ELEV') ||
      upper.contains('WARN') ||
      upper.contains('PRESSURE')) {
    return reactorBadge(label, ReactorStatus.warning);
  }
  if (upper.contains('NORMAL')) {
    return reactorBadge(label, ReactorStatus.healthy);
  }
  return reactorBadge(label, ReactorStatus.neutral);
}

String _localizedIncidentState(String state) {
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
  return state;
}

ReactorStatus _severityStatus(String severity) {
  final String upper = severity.toUpperCase();
  if (upper.contains('CRITICAL') || upper.contains('ERROR')) {
    return ReactorStatus.critical;
  }
  if (upper.contains('WARN')) return ReactorStatus.warning;
  if (upper.contains('INFO')) return ReactorStatus.info;
  return ReactorStatus.neutral;
}

String _formatTime(int epochMs) {
  if (epochMs <= 0) return reactorText(ReactorText.commonUnavailable);
  final DateTime time = DateTime.fromMillisecondsSinceEpoch(epochMs).toLocal();
  String two(int value) => value.toString().padLeft(2, '0');
  return '${time.year}-${two(time.month)}-${two(time.day)} '
      '${two(time.hour)}:${two(time.minute)}:${two(time.second)}';
}

class IncidentCenterView extends StatelessWidget {
  final IncidentStatus status;
  final double? liveScore;
  final String? liveDisplay;

  const IncidentCenterView({
    required this.status,
    this.liveScore,
    this.liveDisplay,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final double score = liveScore ?? status.score;
    final bool scoreAvailable = liveScore != null || status.scoreAvailable;
    final ReactorStatus scoreStatus = !scoreAvailable
        ? ReactorStatus.neutral
        : score >= 70
        ? ReactorStatus.critical
        : score >= 40
        ? ReactorStatus.warning
        : ReactorStatus.healthy;
    final IncidentContributor? primary = status.primaryContributor;
    final List<IncidentContributor> contributors =
        List<IncidentContributor>.of(status.contributors)..sort((
          IncidentContributor left,
          IncidentContributor right,
        ) {
          if (left.available != right.available) return left.available ? -1 : 1;
          return right.scorePoints.compareTo(left.scorePoints);
        });

    return Collection(
      gap: 0,
      children: <Widget>[
        SectionPanel(
          label: reactorText(ReactorText.incidentCurrentState),
          trailing: _stateChip(status.state),
          flush: true,
          child: Collection(
            gap: 0,
            children: <Widget>[
              statGrid(<Widget>[
                ReactorStat(
                  label: reactorText(ReactorText.commonIncidentScore),
                  value: scoreAvailable
                      ? liveDisplay ?? score.toStringAsFixed(1)
                      : reactorText(ReactorText.commonUnavailable),
                  status: scoreStatus,
                ),
                ReactorStat(
                  label: reactorText(ReactorText.shellState),
                  value: _localizedIncidentState(status.state),
                  status: scoreStatus,
                ),
                ReactorStat(
                  label: reactorText(ReactorText.incidentSampledAt),
                  value: _formatTime(status.sampledAtMs),
                  status: ReactorStatus.neutral,
                ),
              ]),
              dom.div(
                styles: const dom.Styles(
                  raw: <String, String>{
                    'padding': '0.75rem',
                    'border-top': '1px solid $kReactorHairline',
                    'display': 'flex',
                    'flex-direction': 'column',
                    'gap': '0.3rem',
                  },
                ),
                <Widget>[
                  reactorEyebrow(
                    reactorText(ReactorText.incidentCurrentDiagnosis),
                  ),
                  Component.text(
                    primary == null || primary.scorePoints <= 0
                        ? reactorText(
                            scoreAvailable
                                ? ReactorText.incidentNoDominantCause
                                : ReactorText.incidentScoreUnavailable,
                          )
                        : reactorText(
                            ReactorText.incidentPrimaryCauseValue,
                            <String, Object?>{
                              'metric': primary.label,
                              'value': primary.display,
                              'points': primary.scorePoints.toStringAsFixed(1),
                            },
                          ),
                  ),
                ],
              ),
            ],
          ),
        ),
        SectionPanel(
          label: reactorText(ReactorText.incidentCenterContributingFactors),
          flush: true,
          child: contributors.isEmpty
              ? ReactorEmptyState(
                  title: reactorText(ReactorText.incidentNoFactors),
                  description: reactorText(
                    ReactorText.incidentNoFactorsDescription,
                  ),
                  icon: ArcaneIcon.listFilter(size: IconSize.sm),
                )
              : dom.div(
                  styles: const dom.Styles(
                    raw: <String, String>{
                      'display': 'flex',
                      'flex-direction': 'column',
                    },
                  ),
                  <Widget>[
                    for (final IncidentContributor contributor in contributors)
                      _ContributorRow(contributor: contributor),
                  ],
                ),
        ),
        SectionPanel(
          label: reactorText(ReactorText.incidentHistory),
          flush: true,
          child: status.incidents.isEmpty
              ? ReactorEmptyState(
                  title: reactorText(ReactorText.incidentNoEvents),
                  description: reactorText(
                    ReactorText.incidentNoEventsDescription,
                  ),
                  icon: ArcaneIcon.clock(size: IconSize.sm),
                )
              : dom.div(
                  styles: const dom.Styles(
                    raw: <String, String>{
                      'display': 'flex',
                      'flex-direction': 'column',
                      'gap': '0.75rem',
                      'padding': '0.75rem',
                    },
                  ),
                  <Widget>[
                    for (final IncidentRecord incident in status.incidents)
                      _IncidentCard(incident: incident),
                  ],
                ),
        ),
      ],
    );
  }
}

class _ContributorRow extends StatelessWidget {
  final IncidentContributor contributor;

  const _ContributorRow({required this.contributor});

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final double fraction = contributor.available
        ? contributor.pressure.clamp(0.0, 1.0)
        : 0;
    final int widthPct = (fraction * 100).round();
    final String value = contributor.available
        ? contributor.display
        : reactorText(ReactorText.commonUnavailable);

    return dom.div(
      styles: const dom.Styles(
        raw: <String, String>{
          'display': 'flex',
          'flex-direction': 'column',
          'gap': '0.3rem',
          'padding': '0.5rem 0.75rem',
          'border-bottom': '1px solid $kReactorHairline',
        },
      ),
      <Widget>[
        dom.div(
          styles: const dom.Styles(
            raw: <String, String>{
              'display': 'flex',
              'justify-content': 'space-between',
              'gap': '0.75rem',
              'font-size': '0.8125rem',
            },
          ),
          <Widget>[
            Component.text(contributor.label),
            Component.text(
              contributor.available
                  ? '$value · ${contributor.scorePoints.toStringAsFixed(1)} ${reactorText(ReactorText.incidentScorePoints)}'
                  : value,
            ),
          ],
        ),
        dom.div(
          styles: const dom.Styles(
            raw: <String, String>{
              'height': '4px',
              'background-color': 'var(--border)',
              'border-radius': '0',
              'overflow': 'hidden',
            },
          ),
          <Widget>[
            dom.div(
              styles: dom.Styles(
                raw: <String, String>{
                  'height': '100%',
                  'width': '$widthPct%',
                  'background-color': contributor.available
                      ? 'var(--primary)'
                      : 'var(--muted-foreground)',
                  'border-radius': '0',
                },
              ),
              <Widget>[],
            ),
          ],
        ),
      ],
    );
  }
}

class _IncidentCard extends StatelessWidget {
  final IncidentRecord incident;

  const _IncidentCard({required this.incident});

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final IncidentLocation? location = incident.location;
    final ReactorStatus severity = _severityStatus(incident.severity);
    return dom.article(
      styles: const dom.Styles(
        raw: <String, String>{
          'border': '1px solid var(--border)',
          'border-radius': kReactorRadius,
          'background-color': 'var(--card)',
          'padding': '0.75rem',
          'display': 'flex',
          'flex-direction': 'column',
          'gap': '0.65rem',
        },
      ),
      <Widget>[
        dom.div(
          styles: const dom.Styles(
            raw: <String, String>{
              'display': 'flex',
              'justify-content': 'space-between',
              'align-items': 'flex-start',
              'gap': '0.75rem',
              'flex-wrap': 'wrap',
            },
          ),
          <Widget>[
            dom.div(
              styles: const dom.Styles(
                raw: <String, String>{
                  'display': 'flex',
                  'flex-direction': 'column',
                  'gap': '0.2rem',
                },
              ),
              <Widget>[
                dom.strong(<Widget>[Component.text(incident.title)]),
                Component.text(
                  '${_formatTime(incident.occurredAtMs)} · ${incident.source}',
                ),
              ],
            ),
            dom.div(
              styles: const dom.Styles(
                raw: <String, String>{
                  'display': 'flex',
                  'gap': '0.4rem',
                  'flex-wrap': 'wrap',
                },
              ),
              <Widget>[
                reactorBadge(incident.phase, severity),
                reactorBadge(incident.severity, severity),
              ],
            ),
          ],
        ),
        Component.text(incident.summary),
        _labeledText(ReactorText.incidentCause, incident.cause),
        if (location != null)
          _labeledText(
            ReactorText.incidentLocation,
            '${location.world} · ${location.x}, ${location.y}, ${location.z}',
          ),
        if (incident.actions.isNotEmpty)
          _detailGroup(reactorText(ReactorText.incidentActions), <Widget>[
            for (final IncidentAction action in incident.actions)
              Component.text(
                '${action.label} [${action.status}]: ${action.detail}',
              ),
          ]),
        if (incident.evidence.isNotEmpty)
          _detailGroup(reactorText(ReactorText.incidentEvidence), <Widget>[
            for (final IncidentContributor evidence in incident.evidence)
              Component.text(
                '${evidence.label}: ${evidence.available ? evidence.display : reactorText(ReactorText.commonUnavailable)}',
              ),
          ]),
        if (incident.context.isNotEmpty)
          _detailGroup(reactorText(ReactorText.incidentDetails), <Widget>[
            for (final MapEntry<String, String> detail
                in incident.context.entries)
              Component.text('${detail.key}: ${detail.value}'),
          ]),
      ],
    );
  }

  Widget _labeledText(ReactorText label, String value) => dom.div(
    styles: const dom.Styles(
      raw: <String, String>{
        'display': 'flex',
        'flex-direction': 'column',
        'gap': '0.15rem',
      },
    ),
    <Widget>[reactorEyebrow(reactorText(label)), Component.text(value)],
  );

  Widget _detailGroup(String label, List<Widget> children) => dom.div(
    styles: const dom.Styles(
      raw: <String, String>{
        'display': 'flex',
        'flex-direction': 'column',
        'gap': '0.2rem',
        'padding-top': '0.45rem',
        'border-top': '1px solid $kReactorHairline',
        'font-size': '0.78rem',
      },
    ),
    <Widget>[reactorEyebrow(label), ...children],
  );
}

class IncidentCenterScreen extends StatefulWidget {
  const IncidentCenterScreen({super.key});

  @override
  State<IncidentCenterScreen> createState() => _IncidentCenterScreenState();
}

class _IncidentCenterScreenState extends State<IncidentCenterScreen> {
  IIncidentClient? _client;
  IncidentStatus? _status;
  Object? _error;
  bool _loading = false;
  Timer? _refreshTimer;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final IIncidentClient? client = OperateScope.of(context)?.client;
    if (identical(client, _client)) return;
    _refreshTimer?.cancel();
    _refreshTimer = null;
    _client = client;
    _status = null;
    _error = null;
    if (client == null) return;
    _load(client);
    _refreshTimer = Timer.periodic(
      _refreshInterval,
      (Timer timer) => _load(client),
    );
  }

  @override
  void dispose() {
    _refreshTimer?.cancel();
    super.dispose();
  }

  Future<void> _load(IIncidentClient client) async {
    if (_loading || !identical(client, _client)) return;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final IncidentStatus status = await client.incidents();
      if (!mounted || !identical(client, _client)) return;
      setState(() {
        _status = status;
        _loading = false;
      });
    } on Object catch (error) {
      if (!mounted || !identical(client, _client)) return;
      setState(() {
        _error = error;
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final ServerScope? server = ServerScope.of(context);
    final SamplerSample? sample = server?.snapshot?.sampler('incident-score');
    final IIncidentClient? client = OperateScope.of(context)?.client;

    if (server?.state == ConnState.connecting && client == null) {
      return _statePage(
        ReactorLoadingState(
          label: reactorText(ReactorText.incidentCenterLoading),
        ),
      );
    }
    if (client == null) {
      return _statePage(
        ReactorNotice(
          title: reactorText(ReactorText.incidentUnavailable),
          message: reactorText(ReactorText.incidentCenterLiveRequired),
          status: ReactorStatus.critical,
        ),
      );
    }
    if (_loading && _status == null) {
      return _statePage(
        ReactorLoadingState(
          label: reactorText(ReactorText.incidentCenterLoading),
        ),
      );
    }

    final Object? error = _error;
    if (error != null && _status == null) {
      return _statePage(
        ReactorNotice(
          title: reactorText(ReactorText.incidentRequestFailed),
          message: localizedReactorError(error),
          status: ReactorStatus.critical,
          action: Button.secondary(
            label: reactorText(ReactorText.commonRetry),
            size: ButtonSize.small,
            onPressed: () => _load(client),
          ),
        ),
      );
    }
    if (_status == null) {
      return _statePage(
        ReactorEmptyState(
          title: reactorText(ReactorText.incidentNoStatus),
          description: reactorText(ReactorText.incidentNoStatusDescription),
          icon: ArcaneIcon.shieldCheck(size: IconSize.sm),
        ),
      );
    }

    return ReactorPage(
      title: reactorText(ReactorText.incidentCenterTitle),
      subtitle: reactorText(ReactorText.incidentCenterSubtitle),
      children: <Widget>[
        if (error != null)
          ReactorNotice(
            title: reactorText(ReactorText.incidentRefreshFailed),
            message: localizedReactorError(error),
            status: ReactorStatus.warning,
            action: Button.secondary(
              label: reactorText(ReactorText.commonRetry),
              size: ButtonSize.small,
              onPressed: () => _load(client),
            ),
          ),
        IncidentCenterView(
          status: _status!,
          liveScore: sample?.available == true ? sample?.value : null,
          liveDisplay: sample?.available == true ? sample?.display : null,
        ),
      ],
    );
  }

  Widget _statePage(Widget state) {
    return ReactorPage(
      title: reactorText(ReactorText.incidentCenterTitle),
      subtitle: reactorText(ReactorText.incidentCenterSubtitle),
      children: <Widget>[state],
    );
  }
}
