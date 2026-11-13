/// A two-to-four-way switch (Simple or Advanced, a time range) and a row of
/// filter chips, both on shadcn's toggle group. Values are mapped to
/// the library's string ids by position, so any value type works. Their
/// height follows the row ([DensityScope]).
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import 'density.dart';

ToggleGroupSize _size(BuildContext context) =>
    DensityScope.isDense(context) ? ToggleGroupSize.sm : ToggleGroupSize.md;

/// One choice: its value and visible label.
class ChoiceOption<T> {
  const ChoiceOption(this.value, this.label);

  final T value;
  final String label;
}

/// Exactly one of [options]. Pressing the chosen one again keeps it.
class ToggleChoice<T> extends StatelessWidget {
  const ToggleChoice({
    required this.label,
    required this.options,
    required this.value,
    required this.onChanged,
    this.disabled = false,
    super.key,
  });

  /// The group's accessible name.
  final String label;
  final List<ChoiceOption<T>> options;
  final T value;
  final void Function(T value) onChanged;
  final bool disabled;

  @override
  Widget build(BuildContext context) {
    final int at = options.indexWhere(
      (ChoiceOption<T> option) => option.value == value,
    );
    return dom.div(
      classes: 'ui-toggle-choice',
      attributes: <String, String>{'role': 'group', 'aria-label': label},
      <Widget>[
        ArcaneToggleGroup(
          items: <ToggleGroupItem>[
            for (int index = 0; index < options.length; index++)
              ToggleGroupItem(
                value: '$index',
                child: Component.text(options[index].label),
              ),
          ],
          value: at < 0 ? null : '$at',
          variant: ToggleGroupVariant.outline,
          size: _size(context),
          disabled: disabled,
          onChanged: (String? picked) {
            final int? index = picked == null ? null : int.tryParse(picked);
            if (index != null && index < options.length) {
              onChanged(options[index].value);
            }
          },
        ),
      ],
    );
  }
}

/// Any number of [options] on or off: list filters.
class ToggleChips<T> extends StatelessWidget {
  const ToggleChips({
    required this.label,
    required this.options,
    required this.values,
    required this.onChanged,
    super.key,
  });

  final String label;
  final List<ChoiceOption<T>> options;
  final Set<T> values;
  final void Function(Set<T> values) onChanged;

  @override
  Widget build(BuildContext context) => dom.div(
    classes: 'ui-toggle-chips',
    attributes: <String, String>{'role': 'group', 'aria-label': label},
    <Widget>[
      ArcaneToggleGroup.multiple(
        items: <ToggleGroupItem>[
          for (int index = 0; index < options.length; index++)
            ToggleGroupItem(
              value: '$index',
              child: Component.text(options[index].label),
            ),
        ],
        values: <String>{
          for (int index = 0; index < options.length; index++)
            if (values.contains(options[index].value)) '$index',
        },
        variant: ToggleGroupVariant.outline,
        size: _size(context),
        onMultiChanged: (Set<String> picked) => onChanged(<T>{
          for (final String id in picked)
            if (int.tryParse(id) case final int index
                when index < options.length)
              options[index].value,
        }),
      ),
    ],
  );
}
