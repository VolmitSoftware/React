library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/sampler_sample.dart';
import '../model/server_snapshot.dart';
import '../state/server_scope.dart';
import '../ui/reactor_ui.dart';
import '../widget/server_snapshot_state.dart';

class MetricsExplorerScreen extends StatefulWidget {
  const MetricsExplorerScreen({super.key});

  @override
  State<MetricsExplorerScreen> createState() => _MetricsExplorerScreenState();
}

class _MetricsExplorerScreenState extends State<MetricsExplorerScreen> {
  String _query = '';

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final ServerScope? scope = ServerScope.of(context);
    final ServerSnapshot? snapshot = scope?.snapshot;
    final Widget? statePage = serverSnapshotStatePage(
      scope: scope,
      title: reactorText(ReactorText.metricsTitle),
      subtitle: reactorText(ReactorText.metricsSubtitle),
      icon: ArcaneIcon.listFilter(size: IconSize.sm),
    );
    if (snapshot == null) return statePage!;

    final List<SamplerSample> samples = snapshot.byId.values.toList();
    samples.sort(
      (SamplerSample left, SamplerSample right) => left.id.compareTo(right.id),
    );
    final String normalizedQuery = _query.trim().toLowerCase();
    final List<SamplerSample> filtered = normalizedQuery.isEmpty
        ? samples
        : samples.where((SamplerSample sample) {
            return sample.id.toLowerCase().contains(normalizedQuery) ||
                sample.name.toLowerCase().contains(normalizedQuery) ||
                sample.suffix.toLowerCase().contains(normalizedQuery);
          }).toList();

    if (samples.isEmpty) {
      return _statePage(
        ReactorEmptyState(
          title: reactorText(ReactorText.metricsNonePublished),
          description: reactorText(ReactorText.metricsNonePublishedDescription),
          icon: ArcaneIcon.listFilter(size: IconSize.sm),
        ),
      );
    }

    return ReactorPage(
      title: reactorText(ReactorText.metricsTitle),
      subtitle: reactorText(ReactorText.metricsSubtitle),
      actions: reactorBadge(
        reactorText(ReactorText.metricsVisibleCount, <String, Object?>{
          'visible': filtered.length,
          'total': samples.length,
        }),
        ReactorStatus.neutral,
      ),
      children: <Widget>[
        SectionPanel(
          label: reactorText(ReactorText.metricsCatalog),
          description: reactorText(ReactorText.metricsCatalogDescription),
          flush: true,
          children: <Widget>[
            dom.div(classes: 'reactor-metrics-toolbar', <Widget>[
              dom.div(classes: 'reactor-metrics-search', <Widget>[
                TextInput(
                  value: _query,
                  placeholder: reactorText(
                    ReactorText.metricsSearchPlaceholder,
                  ),
                  onChange: (String value) => setState(() => _query = value),
                  fullWidth: true,
                ),
              ]),
              dom.span(classes: 'reactor-metrics-updated', <Widget>[
                Component.text(
                  reactorText(ReactorText.metricsSequence, <String, Object?>{
                    'sequence': snapshot.seq,
                  }),
                ),
              ]),
            ]),
            if (filtered.isEmpty)
              ReactorEmptyState(
                title: reactorText(ReactorText.metricsNoMatches),
                description: reactorText(ReactorText.metricsTryDifferentFilter),
                icon: ArcaneIcon.searchX(size: IconSize.sm),
              )
            else
              dom.div(classes: 'reactor-metrics-table-wrap', <Widget>[
                dom.table(classes: 'reactor-metrics-table', <Widget>[
                  dom.thead(<Widget>[
                    dom.tr(<Widget>[
                      _heading(reactorText(ReactorText.metricsSampler)),
                      _heading(reactorText(ReactorText.metricsCurrent)),
                      _heading(reactorText(ReactorText.metricsMinimum)),
                      _heading(reactorText(ReactorText.metricsMaximum)),
                      _heading(reactorText(ReactorText.metricsSamples)),
                    ]),
                  ]),
                  dom.tbody(<Widget>[
                    for (final SamplerSample sample in filtered)
                      dom.tr(<Widget>[
                        dom.td(<Widget>[
                          dom.div(classes: 'reactor-metric-name', <Widget>[
                            Component.text(sample.name),
                          ]),
                          dom.code(classes: 'reactor-metric-id', <Widget>[
                            Component.text(sample.id),
                          ]),
                        ]),
                        _numberCell(sample.display),
                        _numberCell(_format(sample.min, sample.suffix)),
                        _numberCell(_format(sample.max, sample.suffix)),
                        _numberCell(sample.history.length.toString()),
                      ]),
                  ]),
                ]),
              ]),
          ],
        ),
      ],
    );
  }

  Widget _statePage(Widget state) {
    return ReactorPage(
      title: reactorText(ReactorText.metricsTitle),
      subtitle: reactorText(ReactorText.metricsSubtitle),
      children: <Widget>[state],
    );
  }

  Widget _heading(String label) => dom.th(<Widget>[Component.text(label)]);

  Widget _numberCell(String value) =>
      dom.td(classes: 'reactor-metric-number', <Widget>[Component.text(value)]);

  String _format(double value, String suffix) {
    final String number = value == value.roundToDouble()
        ? value.toInt().toString()
        : value.toStringAsFixed(2);
    return suffix.isEmpty ? number : '$number $suffix';
  }
}
