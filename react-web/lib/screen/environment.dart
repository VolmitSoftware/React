library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../model/environment_info.dart';
import '../localization/reactor_localizations.dart';
import '../service/react_client.dart';
import '../state/operate_scope.dart';
import '../ui/reactor_ui.dart';
import '../widget/section_card.dart';

const List<String> _kPreferredOrder = <String>[
  'cpu',
  'memory',
  'jvm',
  'server',
];

final RegExp _kSectionCode = RegExp('§.');

String _sectionLabel(String key) {
  if (key == 'cpu' || key == 'jvm') return key.toUpperCase();
  if (key.isEmpty) return key;
  return key[0].toUpperCase() + key.substring(1);
}

String _humanBytes(double bytes) {
  const List<String> units = <String>['B', 'KB', 'MB', 'GB', 'TB', 'PB'];
  double v = bytes.abs();
  int i = 0;
  while (v >= 1024.0 && i < units.length - 1) {
    v /= 1024.0;
    i++;
  }
  final String text = i == 0
      ? v.toStringAsFixed(0)
      : v.toStringAsFixed(v >= 100.0 ? 0 : 1);
  return '$text ${units[i]}';
}

bool _looksLikeBytes(String section, String key) {
  final String k = key.toLowerCase().replaceAll(' ', '');
  if (k.endsWith('mb') ||
      k.endsWith('kb') ||
      k.endsWith('gb') ||
      k.endsWith('tb') ||
      k.contains('percent') ||
      k.contains('pct')) {
    return false;
  }
  if (k.contains('byte')) return true;
  final String s = section.toLowerCase();
  final bool byteSection =
      s.contains('mem') ||
      s.contains('disk') ||
      s.contains('storage') ||
      s.contains('gpu');
  if (!byteSection) return false;
  if (k.startsWith('physical') ||
      k.startsWith('virtual') ||
      k.startsWith('swap')) {
    return true;
  }
  if (k.contains('vram')) return true;
  return k.endsWith('total') ||
      k.endsWith('free') ||
      k.endsWith('used') ||
      k.endsWith('size') ||
      k.endsWith('capacity');
}

String _formatEnvValue(String section, String key, Object? value) {
  if (value == null) return '';
  if (value is num && _looksLikeBytes(section, key)) {
    return _humanBytes(value.toDouble());
  }
  return value.toString().replaceAll(_kSectionCode, '');
}

class EnvironmentView extends StatelessWidget {
  final EnvironmentInfo info;

  const EnvironmentView({required this.info, super.key});

  @override
  Widget build(BuildContext context) {
    final List<String> ordered = <String>[];
    for (final String k in _kPreferredOrder) {
      if (info.sectionNames.contains(k)) ordered.add(k);
    }
    for (final String k in info.sectionNames) {
      if (!ordered.contains(k)) ordered.add(k);
    }

    return Collection(
      gap: 16,
      children: <Widget>[
        for (final String section in ordered)
          sectionCard(
            label: _sectionLabel(section),
            flush: true,
            child: dom.div(
              styles: const dom.Styles(
                raw: <String, String>{
                  'display': 'flex',
                  'flex-direction': 'column',
                },
              ),
              <Widget>[
                for (final MapEntry<String, Object?> entry in info.entriesOf(
                  section,
                ))
                  dom.div(
                    styles: const dom.Styles(
                      raw: <String, String>{
                        'display': 'flex',
                        'justify-content': 'space-between',
                        'align-items': 'center',
                        'gap': '1rem',
                        'padding': '0.6rem 1.15rem',
                        'border-bottom': '1px solid $kReactorHairline',
                      },
                    ),
                    <Widget>[
                      dom.span(
                        styles: const dom.Styles(
                          raw: <String, String>{
                            'color': 'var(--muted-foreground)',
                            'font-size': '0.85rem',
                          },
                        ),
                        <Widget>[Component.text(entry.key)],
                      ),
                      dom.span(
                        styles: const dom.Styles(
                          raw: <String, String>{
                            'font-size': '0.85rem',
                            'font-weight': '500',
                            'color': 'var(--foreground)',
                            'font-variant-numeric': 'tabular-nums',
                            'text-align': 'right',
                          },
                        ),
                        <Widget>[
                          Component.text(
                            _formatEnvValue(section, entry.key, entry.value),
                          ),
                        ],
                      ),
                    ],
                  ),
              ],
            ),
          ),
      ],
    );
  }
}

class EnvironmentScreen extends StatefulWidget {
  const EnvironmentScreen({super.key});

  @override
  State<EnvironmentScreen> createState() => _EnvironmentScreenState();
}

class _EnvironmentScreenState extends State<EnvironmentScreen> {
  EnvironmentInfo? _info;
  bool _loading = false;
  bool _started = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final IEnvironmentClient? client = OperateScope.of(context)?.client;
    if (client != null && !_started) {
      _started = true;
      _loading = true;
      client
          .environment()
          .then((EnvironmentInfo info) {
            if (!mounted) return;
            setState(() {
              _info = info;
              _loading = false;
            });
          })
          .catchError((Object e) {
            if (!mounted) return;
            setState(() {
              _info = null;
              _loading = false;
            });
          });
    }
  }

  void _refresh() {
    final IEnvironmentClient? client = OperateScope.of(context)?.client;
    if (client == null) return;
    setState(() => _loading = true);
    client
        .environment()
        .then((EnvironmentInfo info) {
          if (!mounted) return;
          setState(() {
            _info = info;
            _loading = false;
          });
        })
        .catchError((Object e) {
          if (!mounted) return;
          setState(() {
            _info = null;
            _loading = false;
          });
        });
  }

  @override
  Widget build(BuildContext context) {
    final IEnvironmentClient? client = OperateScope.of(context)?.client;

    if (client == null) {
      return ReactorPage(
        title: reactorText(ReactorText.environmentTitle),
        subtitle: reactorText(ReactorText.environmentSubtitle),
        children: <Widget>[
          _note(reactorText(ReactorText.environmentLiveRequired)),
        ],
      );
    }

    if (_loading) {
      return ReactorPage(
        title: reactorText(ReactorText.environmentTitle),
        subtitle: reactorText(ReactorText.environmentSubtitle),
        children: <Widget>[_note(reactorText(ReactorText.environmentLoading))],
      );
    }

    if (_info == null) {
      return ReactorPage(
        title: reactorText(ReactorText.environmentTitle),
        subtitle: reactorText(ReactorText.environmentSubtitle),
        children: <Widget>[_note(reactorText(ReactorText.environmentNoData))],
      );
    }

    return ReactorPage(
      title: reactorText(ReactorText.environmentTitle),
      subtitle: reactorText(ReactorText.environmentSubtitle),
      actions: Button.secondary(
        label: reactorText(ReactorText.environmentRefresh),
        size: ButtonSize.small,
        onPressed: _refresh,
      ),
      children: <Widget>[EnvironmentView(info: _info!)],
    );
  }

  Widget _note(String text) => dom.div(
    styles: const dom.Styles(
      raw: <String, String>{
        'color': 'var(--muted-foreground)',
        'font-size': '0.875rem',
      },
    ),
    <Widget>[Component.text(text)],
  );
}
