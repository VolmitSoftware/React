library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../model/incident_status.dart';
import '../model/sampler_sample.dart';
import '../service/react_client.dart';
import '../state/operate_scope.dart';
import '../state/server_scope.dart';
import '../ui/reactor_ui.dart';
import '../widget/gauge.dart';
import '../widget/section_card.dart';

ArcaneStatusBadge _stateChip(String? state) {
  if (state == null) {
    return ArcaneStatusBadge.info('Offline');
  }
  final String upper = state.toUpperCase();
  if (upper.contains('CRIT') || upper.contains('PANIC')) {
    return ArcaneStatusBadge.error(state);
  }
  if (upper.contains('ELEV') ||
      upper.contains('WARN') ||
      upper.contains('PRESSURE')) {
    return ArcaneStatusBadge.warning(state);
  }
  return ArcaneStatusBadge.success(state);
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
    final double gaugeValue = liveScore ?? status.score;

    return Collection(
      gap: 20,
      children: <Widget>[
        dom.div(
          styles: const dom.Styles(raw: <String, String>{
            'display': 'flex',
            'justify-content': 'center',
            'padding': '1rem 0',
          }),
          <Widget>[
            Gauge(
              label: 'Incident Score',
              value: gaugeValue,
              display: liveDisplay,
              max: 100.0,
              thresholds: (40.0, 70.0),
            ),
          ],
        ),
        dom.div(
          styles: const dom.Styles(raw: <String, String>{
            'display': 'flex',
            'justify-content': 'center',
          }),
          <Widget>[_stateChip(status.state)],
        ),
        sectionCard(
          label: 'Incident Timeline',
          child: status.timeline.isEmpty
              ? ArcaneEmptyState.noData()
              : Collection(
                  gap: 4,
                  children: <Widget>[
                    for (final String entry in status.timeline)
                      dom.div(
                        styles: const dom.Styles(raw: <String, String>{
                          'font-size': '0.875rem',
                          'color': 'var(--foreground)',
                        }),
                        <Widget>[Component.text(entry)],
                      ),
                  ],
                ),
        ),
        sectionCard(
          label: 'Contributing Factors',
          child: status.contributors.isEmpty
              ? ArcaneEmptyState.noData()
              : Collection(
                  gap: 8,
                  children: <Widget>[
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
      styles: const dom.Styles(raw: <String, String>{
        'display': 'flex',
        'flex-direction': 'column',
        'gap': '2px',
      }),
      <Widget>[
        dom.div(
          styles: const dom.Styles(raw: <String, String>{
            'display': 'flex',
            'justify-content': 'space-between',
            'font-size': '0.8125rem',
          }),
          <Widget>[
            Component.text(contributor.name),
            Component.text(contributor.value.toStringAsFixed(1)),
          ],
        ),
        dom.div(
          styles: const dom.Styles(raw: <String, String>{
            'height': '4px',
            'background-color': 'var(--border)',
            'border-radius': '2px',
            'overflow': 'hidden',
          }),
          <Widget>[
            dom.div(
              styles: dom.Styles(raw: <String, String>{
                'height': '100%',
                'width': '$widthPct%',
                'background-color': 'var(--primary)',
                'border-radius': '2px',
              }),
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
  IncidentStatus? _status;
  bool _loading = false;
  bool _started = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final IIncidentClient? client =
        OperateScope.of(context)?.client;
    if (client != null && !_started) {
      _started = true;
      _loading = true;
      client.incidents().then((IncidentStatus s) {
        if (!mounted) return;
        setState(() {
          _status = s;
          _loading = false;
        });
      }).catchError((Object _) {
        if (!mounted) return;
        setState(() {
          _status = null;
          _loading = false;
        });
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final SamplerSample? sample =
        ServerScope.of(context)?.snapshot?.sampler('incident-score');
    final IIncidentClient? client = OperateScope.of(context)?.client;

    if (client == null) {
      return ReactorPage(
        title: 'Incident Center',
        subtitle: 'Live incident analysis',
        children: <Widget>[
          sectionCard(
            label: 'Incident Center',
            child: dom.div(
              styles: const dom.Styles(raw: <String, String>{
                'color': 'var(--muted-foreground)',
                'font-size': '0.875rem',
              }),
              <Widget>[
                Component.text(
                  'Incident data requires a live connection.',
                ),
              ],
            ),
          ),
        ],
      );
    }

    if (_loading) {
      return ReactorPage(
        title: 'Incident Center',
        subtitle: 'Live incident analysis',
        children: <Widget>[
          sectionCard(
            label: 'Incident Center',
            child: dom.div(
              styles: const dom.Styles(raw: <String, String>{
                'color': 'var(--muted-foreground)',
                'font-size': '0.875rem',
              }),
              <Widget>[Component.text('Loading incidents...')],
            ),
          ),
        ],
      );
    }

    if (_status == null) {
      return ReactorPage(
        title: 'Incident Center',
        subtitle: 'Live incident analysis',
        children: <Widget>[
          sectionCard(
            label: 'Incident Center',
            child: ArcaneEmptyState.noData(),
          ),
        ],
      );
    }

    return ReactorPage(
      title: 'Incident Center',
      subtitle: 'Live incident analysis',
      children: <Widget>[
        IncidentCenterView(
          status: _status!,
          liveScore: sample?.value,
          liveDisplay: sample?.display,
        ),
      ],
    );
  }
}
