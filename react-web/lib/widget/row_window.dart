import 'package:arcane_jaspr/arcane_jaspr.dart';

class RowWindow<T> extends StatefulWidget {
  final List<T> rows;
  final Widget Function(List<T> rows) builder;
  final int pageSize;
  final bool latest;

  const RowWindow({
    required this.rows,
    required this.builder,
    this.pageSize = 100,
    this.latest = false,
    super.key,
  }) : assert(pageSize > 0);

  @override
  State<RowWindow<T>> createState() => _RowWindowState<T>();
}

class _RowWindowState<T> extends State<RowWindow<T>> {
  int? _page;

  @override
  Widget build(BuildContext context) {
    final int pages =
        ((component.rows.length + component.pageSize - 1) ~/ component.pageSize)
            .clamp(1, 1 << 30);
    final int page = (_page ?? (component.latest ? pages : 1)).clamp(1, pages);
    final int start = (page - 1) * component.pageSize;
    final int end = (start + component.pageSize).clamp(
      0,
      component.rows.length,
    );
    return Widget.fragment(<Widget>[
      if (pages > 1)
        ArcanePagination(
          currentPage: page,
          totalPages: pages,
          showPrevNext: false,
          showFirstLast: false,
          size: PaginationSize.sm,
          onPageChange: (int next) => setState(
            () => _page = component.latest && next == pages ? null : next,
          ),
        ),
      component.builder(component.rows.sublist(start, end)),
    ]);
  }
}
