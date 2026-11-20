/// An accordion whose rows stay as the user left them when the page
/// rebuilds for live data.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component, kIsWeb;
import 'package:universal_web/web.dart' as web;

/// One row of a [KeptOpenAccordion]. [key] names the row across rebuilds,
/// so a row keeps its state when rows are added, removed or reordered.
class KeptOpenItem {
  const KeptOpenItem({
    required this.key,
    required this.title,
    required this.content,
    this.customTitle,
    this.defaultOpen = false,
  });

  final String key;
  final String title;
  final Widget? customTitle;
  final Widget content;

  /// Applies only while the row is not on the page yet.
  final bool defaultOpen;
}

/// Each `<details>` row opens natively, and Jaspr drops an `open` attribute
/// its last build did not write, so every rebuild would fold the rows the
/// user opened. Before each build this reads back which rows are open on the
/// page and writes them open again. [id] must be unique on the page.
class KeptOpenAccordion extends StatelessWidget {
  const KeptOpenAccordion({
    required this.id,
    required this.items,
    this.allowMultiple = true,
    super.key,
  });

  final String id;
  final List<KeptOpenItem> items;
  final bool allowMultiple;

  static const String _keyAttribute = 'data-open-key';

  /// The keys of the rows on the page now, and of those open.
  (Set<String>, Set<String>) _onPage() {
    if (!kIsWeb) return (const <String>{}, const <String>{});
    final Set<String> shown = _keys('#$id [$_keyAttribute]');
    final Set<String> open = _keys('#$id details[open] [$_keyAttribute]');
    return (shown, open);
  }

  static Set<String> _keys(String selector) {
    final web.NodeList found = web.document.querySelectorAll(selector);
    return <String>{
      for (int index = 0; index < found.length; index++)
        ?(found.item(index) as web.Element?)?.getAttribute(_keyAttribute),
    };
  }

  @override
  Widget build(BuildContext context) {
    final (Set<String> shown, Set<String> open) = _onPage();
    return dom.div(id: id, <Widget>[
      ArcaneAccordion(
        allowMultiple: allowMultiple,
        items: <ArcaneAccordionItem>[
          for (final KeptOpenItem item in items)
            ArcaneAccordionItem(
              title: item.title,
              customTitle: dom.div(
                attributes: <String, String>{_keyAttribute: item.key},
                <Widget>[item.customTitle ?? Component.text(item.title)],
              ),
              customContent: item.content,
              defaultOpen: shown.contains(item.key)
                  ? open.contains(item.key)
                  : item.defaultOpen,
            ),
        ],
      ),
    ]);
  }
}
