library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../model/environment_info.dart';
import '../localization/reactor_localizations.dart';
import '../service/react_client.dart';
import '../state/connection_manager.dart';
import '../state/operate_scope.dart';
import '../state/server_scope.dart';
import '../ui/reactor_ui.dart';

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
      gap: 0,
      children: <Widget>[
        for (final String section in ordered)
          SectionPanel(
            label: _sectionLabel(section),
            flush: true,
            child: info.entriesOf(section).isEmpty
                ? const ReactorEmptyState(
                    title: 'No values reported',
                    description: 'This diagnostic section is empty.',
                  )
                : dom.div(
                    styles: const dom.Styles(
                      raw: <String, String>{
                        'display': 'flex',
                        'flex-direction': 'column',
                      },
                    ),
                    <Widget>[
                      for (final MapEntry<String, Object?> entry
                          in info.entriesOf(section))
                        dom.div(
                          styles: const dom.Styles(
                            raw: <String, String>{
                              'display': 'flex',
                              'justify-content': 'space-between',
                              'align-items': 'center',
                              'gap': '1rem',
                              'padding': '0.5rem 0.75rem',
                              'border-bottom': '1px solid $kReactorHairline',
                            },
                          ),
                          <Widget>[
                            dom.span(
                              styles: const dom.Styles(
                                raw: <String, String>{
                                  'color': 'var(--muted-foreground)',
                                  'font-size': '0.78rem',
                                },
                              ),
                              <Widget>[Component.text(entry.key)],
                            ),
                            dom.code(
                              styles: const dom.Styles(
                                raw: <String, String>{
                                  'font-size': '0.74rem',
                                  'font-weight': '500',
                                  'color': 'var(--foreground)',
                                  'font-variant-numeric': 'tabular-nums',
                                  'text-align': 'right',
                                },
                              ),
                              <Widget>[
                                Component.text(
                                  _formatEnvValue(
                                    section,
                                    entry.key,
                                    entry.value,
                                  ),
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
  IEnvironmentClient? _client;
  EnvironmentInfo? _info;
  Object? _error;
  bool _loading = false;
  bool _started = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final IEnvironmentClient? client = OperateScope.of(context)?.client;
    if (client != null && !_started) {
      _started = true;
      _client = client;
      _load(client);
    }
  }

  Future<void> _load(IEnvironmentClient client) async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final EnvironmentInfo info = await client.environment();
      if (!mounted || client != _client) return;
      setState(() {
        _info = info;
        _loading = false;
      });
    } on Object catch (error) {
      if (!mounted || client != _client) return;
      setState(() {
        _error = error;
        _loading = false;
      });
    }
  }

  void _refresh() {
    final IEnvironmentClient? client = _client;
    if (client != null) _load(client);
  }

  @override
  Widget build(BuildContext context) {
    final ServerScope? server = ServerScope.of(context);
    final IEnvironmentClient? client = OperateScope.of(context)?.client;

    if (server?.state == ConnState.connecting && client == null) {
      return _statePage(
        ReactorLoadingState(label: reactorText(ReactorText.environmentLoading)),
      );
    }
    if (client == null) {
      return _statePage(
        ReactorNotice(
          title: 'Environment diagnostics unavailable',
          message: reactorText(ReactorText.environmentLiveRequired),
          status: ReactorStatus.critical,
        ),
      );
    }

    if (_loading && _info == null) {
      return _statePage(
        ReactorLoadingState(label: reactorText(ReactorText.environmentLoading)),
      );
    }

    final Object? error = _error;
    if (error != null && _info == null) {
      return _statePage(
        ReactorNotice(
          title: reactorText(ReactorText.environmentNoData),
          message: error.toString(),
          status: ReactorStatus.critical,
          action: Button.secondary(
            label: 'Retry',
            size: ButtonSize.small,
            onPressed: _refresh,
          ),
        ),
      );
    }

    final EnvironmentInfo? info = _info;
    if (info == null || info.sectionNames.isEmpty) {
      return _statePage(
        ReactorEmptyState(
          title: reactorText(ReactorText.environmentNoData),
          description: 'React returned no host or runtime diagnostics.',
          icon: ArcaneIcon.serverCog(size: IconSize.sm),
        ),
      );
    }

    return ReactorPage(
      title: reactorText(ReactorText.environmentTitle),
      subtitle: reactorText(ReactorText.environmentSubtitle),
      actions: Button.secondary(
        label: _loading
            ? reactorText(ReactorText.environmentLoading)
            : reactorText(ReactorText.environmentRefresh),
        size: ButtonSize.small,
        disabled: _loading,
        onPressed: _loading ? null : _refresh,
      ),
      children: <Widget>[
        if (error != null)
          ReactorNotice(
            title: 'Diagnostic refresh failed',
            message: error.toString(),
            status: ReactorStatus.warning,
            action: Button.secondary(
              label: 'Retry',
              size: ButtonSize.small,
              onPressed: _refresh,
            ),
          )
        else if (_loading)
          const ReactorNotice(
            title: 'Refreshing diagnostics',
            message: 'Keeping the previous values visible until React replies.',
            status: ReactorStatus.info,
          ),
        EnvironmentView(info: info),
      ],
    );
  }

  Widget _statePage(Widget state) {
    return ReactorPage(
      title: reactorText(ReactorText.environmentTitle),
      subtitle: reactorText(ReactorText.environmentSubtitle),
      children: <Widget>[state],
    );
  }
}
