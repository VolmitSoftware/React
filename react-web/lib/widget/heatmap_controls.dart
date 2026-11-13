/// The map's own controls: a select for a toolbar row, the centre and
/// radius fields, which commit on Enter or when a field loses focus, and
/// the failure line of a map request.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;

import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../screen/heatmaps_viewport.dart';
import '../service/react_exceptions.dart';
import '../ui/error_text.dart';
import '../ui/kit/density.dart';
import '../ui/kit/states.dart';

/// A select in a toolbar row: named for assistive technology, no visible
/// label, as tall as the buttons beside it, and narrowing (down to a floor)
/// before the row folds its buttons away.
class HeatmapPick extends StatelessWidget {
  const HeatmapPick({
    required this.label,
    required this.value,
    required this.options,
    required this.onChanged,
    this.placeholder,
    this.disabled = false,
    super.key,
  });

  final String label;
  final String? value;
  final String? placeholder;
  final List<ArcaneSelectOption> options;
  final bool disabled;
  final void Function(String value) onChanged;

  @override
  Widget build(BuildContext context) =>
      dom.div(classes: 'reactor-filter', <Widget>[
        ArcaneSelect(
          ariaLabel: label,
          placeholder: placeholder,
          size: controlSize(DensityScope.of(context)),
          value: value,
          options: options,
          disabled: disabled,
          fullWidth: true,
          onChanged: disabled ? null : onChanged,
        ),
      ]);
}

/// Centre chunk X and Z and the radius of [viewport]. A field keeps what is
/// typed until Enter or blur; a valid change goes to [onCommit], an invalid
/// one stays in the field and is marked.
class HeatmapViewportFields extends StatefulWidget {
  const HeatmapViewportFields({
    required this.viewport,
    required this.onCommit,
    super.key,
  });

  final HeatmapViewport viewport;
  final void Function(HeatmapViewport viewport) onCommit;

  @override
  State<HeatmapViewportFields> createState() => _HeatmapViewportFieldsState();
}

class _HeatmapViewportFieldsState extends State<HeatmapViewportFields> {
  final Map<HeatmapField, String> _drafts = <HeatmapField, String>{};
  final Set<HeatmapField> _invalid = <HeatmapField>{};

  @override
  void didUpdateWidget(HeatmapViewportFields oldComponent) {
    super.didUpdateWidget(oldComponent);
    if (!identical(oldComponent.viewport, component.viewport)) {
      _drafts.clear();
      _invalid.clear();
    }
  }

  int _applied(HeatmapField field) => switch (field) {
    HeatmapField.centerX => component.viewport.centerChunkX,
    HeatmapField.centerZ => component.viewport.centerChunkZ,
    HeatmapField.radius => component.viewport.radius,
  };

  void _commit(HeatmapField field) {
    final String? draft = _drafts[field];
    if (draft == null) return;
    final HeatmapViewport? next = heatmapViewportWith(
      component.viewport,
      field,
      draft,
    );
    if (next == null) {
      setState(() => _invalid.add(field));
      return;
    }
    if (int.parse(draft.trim()) == _applied(field)) {
      setState(() {
        _drafts.remove(field);
        _invalid.remove(field);
      });
      return;
    }
    component.onCommit(next);
  }

  Widget _input(HeatmapField field, ReactorText label, ComponentSize size) =>
      ArcaneFormField(
        controlId: 'heatmap-${field.name}',
        invalid: _invalid.contains(field),
        children: <Widget>[
          ArcaneFieldLabel(reactorText(label)),
          ArcaneFieldContent(
            children: <Widget>[
              TextInput(
                type: TextInputType.number,
                size: size,
                value: _drafts[field] ?? _applied(field).toString(),
                fullWidth: true,
                onChanged: (String value) => _drafts[field] = value,
                onBlur: () => _commit(field),
                onSubmitted: (String _) => _commit(field),
              ),
              ArcaneFieldError(
                _invalid.contains(field)
                    ? reactorText(ReactorText.errorBadRequest)
                    : null,
              ),
            ],
          ),
        ],
      );

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final ComponentSize size = controlSize(Density.dense);
    return DenseBox(
      classes: 'reactor-field-grid reactor-heatmap-fields',
      children: <Widget>[
        _input(HeatmapField.centerX, ReactorText.heatmapsCenterChunkX, size),
        _input(HeatmapField.centerZ, ReactorText.heatmapsCenterChunkZ, size),
        _input(HeatmapField.radius, ReactorText.heatmapsRadiusChunks, size),
      ],
    );
  }
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

/// A failed request; a 403 says the account has no access, and a failure
/// a retry cannot fix offers no Retry.
Widget heatmapFailure(Object error, void Function() retry) {
  final bool forbidden = reactErrorKind(error) == ReactErrorKind.forbidden;
  return ErrorState(
    title: forbidden ? null : reactorText(ReactorText.heatmapsRequestFailed),
    message: localizedReactorError(error),
    onRetry: _lasting(error) ? null : retry,
  );
}
