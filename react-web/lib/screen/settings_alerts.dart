/// Settings > Alerts: the values at which an alert opens, for every server.
/// Edits are kept on Save; Reset asks first.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;

import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/alert_thresholds.dart';
import '../state/fleet_scope.dart';
import '../ui/kit/action_row.dart';
import '../ui/kit/card.dart';
import '../ui/kit/confirm_dialog.dart';
import '../ui/kit/mode.dart';
import '../ui/kit/states.dart';

/// One threshold: its label, unit and place in [AlertThresholds].
enum Threshold {
  tpsWarn(ReactorText.settingsTpsWarn, ''),
  tpsCrit(ReactorText.settingsTpsCritical, ''),
  msptWarn(ReactorText.settingsMsptWarn, 'ms'),
  incidentScoreWarn(ReactorText.settingsIncidentScoreWarn, ''),
  gcPercentWarn(ReactorText.settingsGcPercentWarn, '%'),
  pingP95Warn(ReactorText.settingsPingP95Warn, 'ms'),
  memoryPressureWarn(ReactorText.settingsMemoryPressureWarn, '%');

  const Threshold(this.label, this.unit);

  final ReactorText label;
  final String unit;

  /// TPS and tick time are the ones Simple shows; the rest wait in
  /// Advanced and keep their saved values.
  bool get simple => this == tpsWarn || this == tpsCrit || this == msptWarn;

  double of(AlertThresholds t) => switch (this) {
    tpsWarn => t.tpsWarn,
    tpsCrit => t.tpsCrit,
    msptWarn => t.msptWarn,
    incidentScoreWarn => t.incidentScoreWarn,
    gcPercentWarn => t.gcPercentWarn,
    pingP95Warn => t.pingP95Warn,
    memoryPressureWarn => t.memoryPressureWarn,
  };

  /// [t] with this threshold set to [value].
  AlertThresholds set(AlertThresholds t, double value) => switch (this) {
    tpsWarn => t.copyWith(tpsWarn: value),
    tpsCrit => t.copyWith(tpsCrit: value),
    msptWarn => t.copyWith(msptWarn: value),
    incidentScoreWarn => t.copyWith(incidentScoreWarn: value),
    gcPercentWarn => t.copyWith(gcPercentWarn: value),
    pingP95Warn => t.copyWith(pingP95Warn: value),
    memoryPressureWarn => t.copyWith(memoryPressureWarn: value),
  };
}

/// [value] as typed in a box: whole numbers without ".0".
String formatThreshold(double value) => value == value.truncateToDouble()
    ? value.toInt().toString()
    : value.toString();

/// A typed threshold, or null when it is not a number of zero or more.
double? parseThreshold(String text) {
  final double? value = double.tryParse(text.trim());
  if (value == null || !value.isFinite || value < 0) return null;
  return value;
}

class SettingsAlertsTab extends StatefulWidget {
  const SettingsAlertsTab({super.key});

  @override
  State<SettingsAlertsTab> createState() => _SettingsAlertsTabState();
}

class _SettingsAlertsTabState extends State<SettingsAlertsTab> {
  /// What each box holds while edited; a missing box shows the saved value.
  final Map<Threshold, String> _drafts = <Threshold, String>{};
  final Set<Threshold> _invalid = <Threshold>{};

  void _save(FleetController fleet) {
    AlertThresholds next = fleet.alertStore.thresholds;
    final Set<Threshold> invalid = <Threshold>{};
    for (final MapEntry<Threshold, String> draft in _drafts.entries) {
      final double? value = parseThreshold(draft.value);
      if (value == null) {
        invalid.add(draft.key);
      } else {
        next = draft.key.set(next, value);
      }
    }
    if (invalid.isNotEmpty) {
      setState(() {
        _invalid
          ..clear()
          ..addAll(invalid);
      });
      return;
    }
    fleet.alertStore.thresholds = next;
    setState(() {
      _drafts.clear();
      _invalid.clear();
    });
    ArcaneSonner.success(reactorText(ReactorText.settingsThresholdsSaved));
  }

  Future<void> _reset(FleetController fleet) async {
    final bool yes = await askToConfirm(
      title: reactorText(ReactorText.settingsResetThresholdsTitle),
      confirmLabel: reactorText(ReactorText.settingsResetDefaults),
      destructive: true,
    );
    if (!yes || !mounted) return;
    fleet.alertStore.thresholds = AlertThresholds.defaults;
    setState(() {
      _drafts.clear();
      _invalid.clear();
    });
  }

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final FleetController? fleet = FleetScope.of(context);
    if (fleet == null) {
      return EmptyState(
        message: reactorText(ReactorText.settingsFleetUnavailable),
      );
    }
    final AlertThresholds saved = fleet.alertStore.thresholds;
    final bool advanced = ModeScope.advanced(context);
    return AppCard(
      title: reactorText(ReactorText.settingsAlertThresholds),
      tip: reactorText(ReactorText.settingsThresholdDescription),
      children: <Widget>[
        dom.div(classes: 'reactor-field-grid', <Widget>[
          for (final Threshold threshold in Threshold.values)
            if (advanced || threshold.simple)
              ArcaneFormField(
                controlId: 'threshold-${threshold.name}',
                invalid: _invalid.contains(threshold),
                children: <Widget>[
                  ArcaneFieldLabel(
                    threshold.unit.isEmpty
                        ? reactorText(threshold.label)
                        : '${reactorText(threshold.label)} (${threshold.unit})',
                  ),
                  ArcaneFieldContent(
                    children: <Widget>[
                      TextInput(
                        type: TextInputType.number,
                        value:
                            _drafts[threshold] ??
                            formatThreshold(threshold.of(saved)),
                        fullWidth: true,
                        onChanged: (String text) => _drafts[threshold] = text,
                        onSubmitted: (String text) {
                          _drafts[threshold] = text;
                          _save(fleet);
                        },
                      ),
                      ArcaneFieldError(
                        _invalid.contains(threshold)
                            ? reactorText(ReactorText.settingsThresholdInvalid)
                            : null,
                      ),
                    ],
                  ),
                ],
              ),
        ]),
        FormFooter(
          secondary: <RowAction>[
            RowAction(
              reactorText(ReactorText.settingsResetDefaults),
              () => _reset(fleet),
            ),
          ],
          primary: RowAction(
            reactorText(ReactorText.configEditorSave),
            () => _save(fleet),
          ),
        ),
      ],
    );
  }
}
