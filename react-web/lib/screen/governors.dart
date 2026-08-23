library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../model/control_item.dart';
import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/sampler_sample.dart';
import '../service/react_client.dart';
import '../state/connection_manager.dart';
import '../state/control_list_controller.dart';
import '../state/control_scope.dart';
import '../state/server_scope.dart';
import '../ui/reactor_ui.dart';
import '../widget/section_card.dart' show statGrid;
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
    dependOnReactorLocale(context);
    final bool hasMetrics =
        incidentScore != null ||
        schedulerBacklog != null ||
        backlogGrowthRate != null;
    return Collection(
      gap: 0,
      children: <Widget>[
        if (hasMetrics)
          SectionPanel(
            label: reactorText(ReactorText.governorsRuntimePressure),
            flush: true,
            child: statGrid(<Widget>[
              if (incidentScore != null)
                StatTile(
                  label: reactorText(ReactorText.commonIncidentScore),
                  sample: incidentScore,
                  status: _scoreStatus(incidentScore!.value),
                ),
              if (schedulerBacklog != null)
                StatTile(
                  label: reactorText(ReactorText.commonSchedulerBacklog),
                  sample: schedulerBacklog,
                ),
              if (backlogGrowthRate != null)
                StatTile(
                  label: reactorText(ReactorText.governorsBacklogGrowthRate),
                  sample: backlogGrowthRate,
                ),
            ]),
          ),
        SectionPanel(
          label: reactorText(ReactorText.governorsSection),
          flush: true,
          child: governors.isEmpty
              ? ReactorEmptyState(
                  title: reactorText(ReactorText.governorsNoneAvailable),
                  description: reactorText(
                    ReactorText.governorsNoneAvailableDescription,
                  ),
                  icon: ArcaneIcon.signal(size: IconSize.sm),
                )
              : dom.div(
                  styles: const dom.Styles(
                    raw: <String, String>{
                      'display': 'flex',
                      'flex-direction': 'column',
                    },
                  ),
                  <Widget>[
                    for (final ControlItem governor in governors)
                      _governorRow(governor),
                  ],
                ),
        ),
      ],
    );
  }

  ReactorStatus _scoreStatus(double score) {
    if (score >= 70) return ReactorStatus.critical;
    if (score >= 40) return ReactorStatus.warning;
    return ReactorStatus.healthy;
  }

  Widget _governorRow(ControlItem governor) {
    return dom.div(
      styles: const dom.Styles(
        raw: <String, String>{
          'display': 'flex',
          'align-items': 'center',
          'justify-content': 'space-between',
          'gap': '0.75rem',
          'padding': '0.6rem 0.75rem',
          'border-bottom': '1px solid $kReactorHairline',
        },
      ),
      <Widget>[
        dom.div(
          styles: const dom.Styles(
            raw: <String, String>{
              'display': 'flex',
              'min-width': '0',
              'flex-direction': 'column',
              'gap': '0.2rem',
            },
          ),
          <Widget>[
            dom.strong(
              styles: const dom.Styles(
                raw: <String, String>{'font-size': '0.8rem'},
              ),
              <Widget>[Component.text(governor.name)],
            ),
            dom.code(
              styles: const dom.Styles(
                raw: <String, String>{
                  'color': kReactorMuted,
                  'font-size': '0.66rem',
                },
              ),
              <Widget>[Component.text(governor.id)],
            ),
          ],
        ),
        dom.div(
          styles: const dom.Styles(
            raw: <String, String>{
              'display': 'flex',
              'align-items': 'center',
              'gap': '0.5rem',
            },
          ),
          <Widget>[
            governor.enabled
                ? reactorBadge(
                    reactorText(ReactorText.commonEnabled),
                    ReactorStatus.healthy,
                  )
                : reactorBadge(
                    reactorText(ReactorText.commonDisabled),
                    ReactorStatus.neutral,
                  ),
            ArcaneToggleSwitch(
              value: governor.enabled,
              disabled: onToggle == null,
              onChanged: onToggle == null
                  ? null
                  : (bool enabled) => onToggle?.call(governor.id, enabled),
            ),
          ],
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
        onError: (Object e) => ArcaneSonner.error(
          reactorText(ReactorText.commonUpdateFailed),
          description: localizedReactorError(e),
        ),
      );
      _controller!.load();
    }
  }

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final ServerScope? scope = ServerScope.of(context);
    final SamplerSample? incidentScore = scope?.snapshot?.sampler(
      'incident-score',
    );
    final SamplerSample? schedulerBacklog = scope?.snapshot?.sampler(
      'scheduler-backlog',
    );
    final SamplerSample? backlogGrowthRate = scope?.snapshot?.sampler(
      'backlog-growth-rate',
    );

    final List<ControlItem> governors = _controller == null
        ? const <ControlItem>[]
        : _controller!.items
              .where((ControlItem i) => kGovernorFeatureIds.contains(i.id))
              .toList();

    if (scope?.state == ConnState.connecting &&
        scope?.snapshot == null &&
        _client == null) {
      return _statePage(
        ReactorLoadingState(
          label: reactorText(ReactorText.governorsLoadingState),
        ),
      );
    }
    if (_client == null) {
      return ReactorPage(
        title: reactorText(ReactorText.governorsTitle),
        subtitle: reactorText(ReactorText.governorsSubtitle),
        children: <Widget>[
          ReactorNotice(
            title: reactorText(ReactorText.governorsUnavailable),
            message: reactorText(ReactorText.governorsLiveRequired),
            status: ReactorStatus.critical,
          ),
          GovernorDashboardView(
            governors: governors,
            incidentScore: incidentScore,
            schedulerBacklog: schedulerBacklog,
            backlogGrowthRate: backlogGrowthRate,
          ),
        ],
      );
    }

    final ControlListController controller = _controller!;
    final bool live = scope?.state == ConnState.live;
    if (controller.loading && controller.items.isEmpty) {
      return _statePage(
        ReactorLoadingState(
          label: reactorText(ReactorText.governorsLoadingState),
        ),
      );
    }
    final Object? error = controller.error;
    if (error != null && controller.items.isEmpty) {
      return _statePage(
        ReactorNotice(
          title: reactorText(ReactorText.commonUpdateFailed),
          message: localizedReactorError(error),
          status: ReactorStatus.critical,
          action: Button.secondary(
            label: reactorText(ReactorText.commonRetry),
            size: ButtonSize.small,
            onPressed: controller.load,
          ),
        ),
      );
    }

    return ReactorPage(
      title: reactorText(ReactorText.governorsTitle),
      subtitle: reactorText(ReactorText.governorsSubtitle),
      children: <Widget>[
        if (error != null)
          ReactorNotice(
            title: reactorText(ReactorText.commonUpdateFailed),
            message: localizedReactorError(error),
            status: ReactorStatus.warning,
            action: Button.secondary(
              label: reactorText(ReactorText.commonRetry),
              size: ButtonSize.small,
              onPressed: controller.load,
            ),
          ),
        GovernorDashboardView(
          governors: governors,
          incidentScore: incidentScore,
          schedulerBacklog: schedulerBacklog,
          backlogGrowthRate: backlogGrowthRate,
          onToggle: live
              ? (String id, bool enabled) {
                  if (ServerScope.of(context)?.state == ConnState.live) {
                    controller.toggle(id, enabled);
                  }
                }
              : null,
        ),
      ],
    );
  }

  Widget _statePage(Widget state) {
    return ReactorPage(
      title: reactorText(ReactorText.governorsTitle),
      subtitle: reactorText(ReactorText.governorsSubtitle),
      children: <Widget>[state],
    );
  }
}
