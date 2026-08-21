library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../model/incident_status.dart';
import '../localization/reactor_localizations.dart';
import '../model/sampler_sample.dart';
import '../service/react_client.dart';
import '../state/connection_manager.dart';
import '../state/operate_scope.dart';
import '../state/server_scope.dart';
import '../ui/reactor_ui.dart';
import '../widget/section_card.dart' show statGrid;

Widget _stateChip(String? state) {
  if (state == null) {
    return reactorBadge(
      reactorText(ReactorText.statusOffline),
      ReactorStatus.neutral,
    );
  }
  final String upper = state.toUpperCase();
  if (upper.contains('CRIT') || upper.contains('PANIC')) {
    return reactorBadge(state, ReactorStatus.critical);
  }
  if (upper.contains('ELEV') ||
      upper.contains('WARN') ||
      upper.contains('PRESSURE')) {
    return reactorBadge(state, ReactorStatus.warning);
  }
  return reactorBadge(state, ReactorStatus.healthy);
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
    final double score = liveScore ?? status.score;
    final ReactorStatus scoreStatus = score >= 70
        ? ReactorStatus.critical
        : score >= 40
        ? ReactorStatus.warning
        : ReactorStatus.healthy;

    return Collection(
      gap: 0,
      children: <Widget>[
        SectionPanel(
          label: 'Current state',
          trailing: _stateChip(status.state),
          flush: true,
          child: statGrid(<Widget>[
            ReactorStat(
              label: reactorText(ReactorText.commonIncidentScore),
              value: liveDisplay ?? score.toStringAsFixed(1),
              status: scoreStatus,
            ),
            ReactorStat(
              label: reactorText(ReactorText.shellState),
              value: status.state,
              status: scoreStatus,
            ),
          ]),
        ),
        SectionPanel(
          label: reactorText(ReactorText.commonIncidentTimeline),
          flush: true,
          child: status.timeline.isEmpty
              ? ReactorEmptyState(
                  title: 'No incident events',
                  description: 'React has not recorded an incident timeline.',
                  icon: ArcaneIcon.clock(size: IconSize.sm),
                )
              : dom.div(
                  styles: const dom.Styles(
                    raw: <String, String>{
                      'display': 'flex',
                      'flex-direction': 'column',
                    },
                  ),
                  <Widget>[
                    for (final String entry in status.timeline)
                      dom.div(
                        styles: const dom.Styles(
                          raw: <String, String>{
                            'padding': '0.5rem 0.75rem',
                            'border-bottom': '1px solid $kReactorHairline',
                            'font-size': '0.78rem',
                            'color': 'var(--foreground)',
                          },
                        ),
                        <Widget>[Component.text(entry)],
                      ),
                  ],
                ),
        ),
        SectionPanel(
          label: reactorText(ReactorText.incidentCenterContributingFactors),
          flush: true,
          child: status.contributors.isEmpty
              ? ReactorEmptyState(
                  title: 'No contributing factors',
                  description:
                      'React did not attribute the current incident score.',
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
                    for (final IncidentContributor c in status.contributors)
                      _ContributorRow(contributor: c),
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
    final double fraction = contributor.weight.clamp(0.0, 1.0);
    final int widthPct = (fraction * 100).round();

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
              'font-size': '0.8125rem',
            },
          ),
          <Widget>[
            Component.text(contributor.name),
            Component.text(contributor.value.toStringAsFixed(1)),
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
                  'background-color': 'var(--primary)',
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
  bool _started = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final IIncidentClient? client = OperateScope.of(context)?.client;
    if (client != null && !_started) {
      _started = true;
      _client = client;
      _load(client);
    }
  }

  Future<void> _load(IIncidentClient client) async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final IncidentStatus status = await client.incidents();
      if (!mounted || client != _client) return;
      setState(() {
        _status = status;
        _loading = false;
      });
    } on Object catch (error) {
      if (!mounted || client != _client) return;
      setState(() {
        _error = error;
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
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
    if (client == null || server?.state == ConnState.offline) {
      return _statePage(
        ReactorNotice(
          title: reactorText(ReactorText.statusOffline),
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
          title: 'Incident request failed',
          message: error.toString(),
          status: ReactorStatus.critical,
          action: Button.secondary(
            label: 'Retry',
            size: ButtonSize.small,
            onPressed: () => _load(client),
          ),
        ),
      );
    }

    if (_status == null) {
      return _statePage(
        ReactorEmptyState(
          title: 'No incident status',
          description: 'React returned no current incident record.',
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
            title: 'Incident refresh failed',
            message: error.toString(),
            status: ReactorStatus.warning,
            action: Button.secondary(
              label: 'Retry',
              size: ButtonSize.small,
              onPressed: () => _load(client),
            ),
          )
        else if (server?.state == ConnState.degraded)
          const ReactorNotice(
            title: 'Connection degraded',
            message: 'Showing the most recent incident status from React.',
            status: ReactorStatus.warning,
          ),
        IncidentCenterView(
          status: _status!,
          liveScore: sample?.value,
          liveDisplay: sample?.display,
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
