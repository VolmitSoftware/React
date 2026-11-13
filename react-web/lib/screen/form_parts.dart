/// A select for a filter in an action row, shared by the server group,
/// alerts and comparison screens.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;

import '../ui/kit/density.dart';

/// A filter for an action row: a library select named by [label] and by
/// its first option ("All servers"). As tall as its row (regular in a page
/// header, dense in a card head).
class FilterSelect extends StatelessWidget {
  const FilterSelect({
    required this.id,
    required this.label,
    required this.value,
    required this.options,
    required this.onChanged,
    super.key,
  });

  final String id;

  /// The select's accessible name.
  final String label;
  final String value;
  final List<ArcaneSelectOption> options;
  final void Function(String value) onChanged;

  @override
  Widget build(BuildContext context) =>
      dom.div(classes: 'reactor-filter', <Widget>[
        ArcaneSelect(
          id: id,
          ariaLabel: label,
          size: controlSize(DensityScope.of(context)),
          value: value,
          options: options,
          fullWidth: true,
          onChanged: onChanged,
        ),
      ]);
}
