library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../model/control_item.dart';
import '../model/sampler_sample.dart';
import '../service/react_client.dart';
import '../state/control_list_controller.dart';
import '../state/control_scope.dart';
import '../state/server_scope.dart';
import '../ui/reactor_ui.dart';
import '../widget/gauge.dart';
import '../widget/section_card.dart';
import '../widget/stat_tile.dart';

const Set<String> kGovernorFeatureIds = <String>{
  'dynamic-view-distance',
  'dynamic-activation-range',
  'activation-range-governor',
  'tracker-range-governor',
  'random-tick-governor',
  'pathfinder-budget',
  'per-world-tick-budget',
  'afk-view-shedding',
  'adaptive-entity-sleep',
  'incident-mode',
  'feature-trinity-incident-mode',
  'circuit-manager',
  'feature-adapt-runtime-surge-guard',
  'feature-iris-terrain-surge-guard',
};

class GovernorDashboardView extends StatelessWidget {
  final List<ControlItem> governors;
  final SamplerSample? incidentScore;
  final SamplerSample? schedulerBacklog;
  final SamplerSample? backlogGrowthRate;
  final void Function(String id, bool enabled)? onToggle;

  const GovernorDashboardView({
    required this.governors,
    this.incidentScore,
    this.schedulerBacklog,
    this.backlogGrowthRate,
    this.onToggle,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
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
              value: incidentScore?.value ?? 0.0,
              display: incidentScore?.display,
              max: 100.0,
              thresholds: (40.0, 70.0),
            ),
          ],
        ),
        statGrid(<Widget>[
          StatTile(label: 'Scheduler Backlog', sample: schedulerBacklog),
          StatTile(label: 'Backlog Growth Rate', sample: backlogGrowthRate),
        ]),
        sectionCard(
          label: 'Governors',
          child: statGrid(<Widget>[
            for (final ControlItem governor in governors)
              Card.elevated(
                fillWidth: true,
                child: dom.div(
                  styles: const dom.Styles(raw: <String, String>{
                    'padding': '0.75rem',
                    'display': 'flex',
                    'flex-direction': 'column',
                    'gap': '0.5rem',
                  }),
                  <Widget>[
                    Text.heading3(governor.name),
                    governor.enabled
                        ? const ArcaneStatusBadge.success(
                            'Enabled',
                            showPulse: false,
                          )
                        : const ArcaneStatusBadge.offline('Disabled'),
                    ArcaneToggleSwitch(
                      value: governor.enabled,
                      onChanged: (bool b) => onToggle?.call(governor.id, b),
                    ),
                  ],
                ),
              ),
          ]),
        ),
      ],
    );
  }
}

class GovernorsScreen extends StatefulWidget {
  const GovernorsScreen({super.key});

  @override
  State<GovernorsScreen> createState() => _GovernorsScreenState();
}

class _GovernorsScreenState extends State<GovernorsScreen> {
  IControlClient? _client;
  ControlListController? _controller;
  bool _started = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final IControlClient? c = ControlScope.of(context)?.client;
    if (c != null && !_started) {
      _started = true;
      _client = c;
      _controller = ControlListController(
        c,
        isTweaks: false,
        onChange: () => setState(() {}),
        onError: (Object e) =>
            ArcaneSonner.error('Update failed', description: e.toString()),
      );
      _controller!.load();
    }
  }

  @override
  Widget build(BuildContext context) {
    final ServerScope? scope = ServerScope.of(context);
    final SamplerSample? incidentScore =
        scope?.snapshot?.sampler('incident-score');
    final SamplerSample? schedulerBacklog =
        scope?.snapshot?.sampler('scheduler-backlog');
    final SamplerSample? backlogGrowthRate =
        scope?.snapshot?.sampler('backlog-growth-rate');

    final List<ControlItem> governors = _controller == null
        ? const <ControlItem>[]
        : _controller!.items
            .where((ControlItem i) => kGovernorFeatureIds.contains(i.id))
            .toList();

    if (_client == null) {
      return ReactorPage(
        title: 'Governors',
        subtitle: 'Adaptive load governors',
        children: <Widget>[
          sectionCard(
            label: 'Governor Control',
            child: dom.div(
              styles: const dom.Styles(raw: <String, String>{
                'color': 'var(--muted-foreground)',
                'font-size': '0.875rem',
              }),
              <Widget>[
                Component.text(
                  'Governor control requires a live connection.',
                ),
              ],
            ),
          ),
          GovernorDashboardView(
            governors: const <ControlItem>[],
            incidentScore: incidentScore,
            schedulerBacklog: schedulerBacklog,
            backlogGrowthRate: backlogGrowthRate,
            onToggle: null,
          ),
        ],
      );
    }

    return ReactorPage(
      title: 'Governors',
      subtitle: 'Adaptive load governors',
      children: <Widget>[
        GovernorDashboardView(
          governors: governors,
          incidentScore: incidentScore,
          schedulerBacklog: schedulerBacklog,
          backlogGrowthRate: backlogGrowthRate,
          onToggle: _controller!.toggle,
        ),
      ],
    );
  }
}
