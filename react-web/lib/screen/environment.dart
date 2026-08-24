library;

import 'dart:async' show Timer;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../chart/timeseries_chart.dart';
import '../model/environment_info.dart';
import '../model/ring_buffer.dart';
import '../localization/reactor_locale.dart';
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

String _humanRate(double bytesPerSecond) => '${_humanBytes(bytesPerSecond)}/s';

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
  final List<double> diskReadHistory;
  final List<double> diskWriteHistory;
  final List<double> networkReceiveHistory;
  final List<double> networkSendHistory;

  const EnvironmentView({
    required this.info,
    this.diskReadHistory = const <double>[],
    this.diskWriteHistory = const <double>[],
    this.networkReceiveHistory = const <double>[],
    this.networkSendHistory = const <double>[],
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
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
                ? ReactorEmptyState(
                    title: reactorText(ReactorText.environmentNoValues),
                    description: reactorText(
                      ReactorText.environmentEmptySection,
                    ),
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
                                  'text-align': 'end',
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
        if (info.disks.isNotEmpty || info.mounts.isNotEmpty) _diskPanel(),
        if (info.network.isNotEmpty) _networkPanel(),
      ],
    );
  }

  Widget _diskPanel() {
    return SectionPanel(
      label: 'Disk',
      flush: true,
      child: dom.div(classes: 'reactor-environment-section', <Widget>[
        dom.div(classes: 'reactor-environment-chart', <Widget>[
          dom.div(classes: 'reactor-subsection-heading', <Widget>[
            reactorEyebrow('Read / write throughput'),
          ]),
          TimeseriesChart(
            series: <(String, List<double>)>[
              ('Read /s', diskReadHistory),
              ('Write /s', diskWriteHistory),
            ],
            height: 180,
            valueFormatter: _humanRate,
          ),
        ]),
        if (info.mounts.isNotEmpty)
          dom.div(classes: 'reactor-environment-capacity-list', <Widget>[
            for (final EnvironmentMount mount in info.mounts)
              _mountCapacity(mount),
          ]),
        if (info.disks.isNotEmpty)
          dom.div(classes: 'reactor-environment-device-grid', <Widget>[
            for (final EnvironmentDisk disk in info.disks)
              _deviceCard(
                title: disk.name,
                subtitle: disk.model,
                values: <String>[
                  'Capacity ${_humanBytes(disk.sizeBytes.toDouble())}',
                  'Read ${_humanBytes(disk.readBytes.toDouble())}',
                  'Written ${_humanBytes(disk.writeBytes.toDouble())}',
                ],
              ),
          ]),
      ]),
    );
  }

  Widget _networkPanel() {
    return SectionPanel(
      label: 'Network',
      flush: true,
      child: dom.div(classes: 'reactor-environment-section', <Widget>[
        dom.div(classes: 'reactor-environment-chart', <Widget>[
          dom.div(classes: 'reactor-subsection-heading', <Widget>[
            reactorEyebrow('Receive / send throughput'),
          ]),
          TimeseriesChart(
            series: <(String, List<double>)>[
              ('Received /s', networkReceiveHistory),
              ('Sent /s', networkSendHistory),
            ],
            height: 180,
            valueFormatter: _humanRate,
          ),
        ]),
        dom.div(classes: 'reactor-environment-device-grid', <Widget>[
          for (final EnvironmentNetworkInterface item in info.network)
            _deviceCard(
              title: item.displayName.isEmpty ? item.name : item.displayName,
              subtitle: item.displayName.isEmpty ? '' : item.name,
              values: <String>[
                'MTU ${item.mtu}',
                if (item.speedBitsPerSecond > 0)
                  'Link ${_humanRate(item.speedBitsPerSecond / 8)}',
                'Received ${_humanBytes(item.receivedBytes.toDouble())}',
                'Sent ${_humanBytes(item.sentBytes.toDouble())}',
              ],
            ),
        ]),
      ]),
    );
  }

  Widget _mountCapacity(EnvironmentMount mount) {
    final String label = mount.mount.isEmpty ? mount.name : mount.mount;
    final String details =
        '${_humanBytes(mount.usedBytes.toDouble())} used · '
        '${_humanBytes(mount.freeBytes.toDouble())} free · '
        '${_humanBytes(mount.totalBytes.toDouble())} total';
    return dom.div(
      classes: 'reactor-environment-capacity',
      attributes: <String, String>{'title': '$label — $details'},
      <Widget>[
        dom.div(classes: 'reactor-environment-capacity-label', <Widget>[
          dom.strong(<Widget>[Component.text(label)]),
          dom.span(<Widget>[Component.text(details)]),
        ]),
        dom.div(
          classes: 'reactor-environment-capacity-track',
          attributes: <String, String>{
            'role': 'img',
            'aria-label': '$label — $details',
          },
          <Widget>[
            dom.span(
              classes: 'reactor-environment-capacity-used',
              styles: dom.Styles(
                raw: <String, String>{
                  'width': '${(mount.usedFraction * 100).toStringAsFixed(2)}%',
                },
              ),
              const <Widget>[],
            ),
          ],
        ),
      ],
    );
  }

  Widget _deviceCard({
    required String title,
    required String subtitle,
    required List<String> values,
  }) {
    final String details = values.join(' · ');
    return dom.div(
      classes: 'reactor-environment-device',
      attributes: <String, String>{'title': details},
      <Widget>[
        dom.strong(<Widget>[Component.text(title)]),
        if (subtitle.isNotEmpty)
          dom.span(classes: 'reactor-environment-device-subtitle', <Widget>[
            Component.text(subtitle),
          ]),
        dom.code(<Widget>[Component.text(details)]),
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
  static const Duration _pollInterval = Duration(seconds: 5);

  IEnvironmentClient? _client;
  EnvironmentInfo? _info;
  Timer? _pollTimer;
  final RingBuffer _diskReadHistory = RingBuffer(48);
  final RingBuffer _diskWriteHistory = RingBuffer(48);
  final RingBuffer _networkReceiveHistory = RingBuffer(48);
  final RingBuffer _networkSendHistory = RingBuffer(48);
  int? _previousDiskReadBytes;
  int? _previousDiskWriteBytes;
  int? _previousNetworkReceiveBytes;
  int? _previousNetworkSendBytes;
  int? _previousSampleMillis;
  Object? _error;
  bool _loading = false;
  bool _requesting = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final IEnvironmentClient? client = OperateScope.of(context)?.client;
    if (client == _client) {
      return;
    }
    _pollTimer?.cancel();
    _client = client;
    if (client != null) {
      _load(client);
      _pollTimer = Timer.periodic(_pollInterval, (Timer _) {
        _load(client, announce: false);
      });
    }
  }

  Future<void> _load(IEnvironmentClient client, {bool announce = true}) async {
    if (_requesting) return;
    _requesting = true;
    if (announce) {
      setState(() {
        _loading = true;
        _error = null;
      });
    }
    try {
      final EnvironmentInfo info = await client.environment();
      if (!mounted || client != _client) return;
      _recordSample(info);
      setState(() {
        _info = info;
        _error = null;
        _loading = false;
      });
    } on Object catch (error) {
      if (!mounted || client != _client) return;
      setState(() {
        _error = error;
        _loading = false;
      });
    } finally {
      _requesting = false;
    }
  }

  void _recordSample(EnvironmentInfo info) {
    final int now = DateTime.now().millisecondsSinceEpoch;
    final int? previous = _previousSampleMillis;
    final double seconds = previous == null
        ? 0
        : ((now - previous) / 1000).clamp(0.001, 60.0);
    _appendRate(
      _diskReadHistory,
      info.totalDiskReadBytes,
      _previousDiskReadBytes,
      seconds,
    );
    _appendRate(
      _diskWriteHistory,
      info.totalDiskWriteBytes,
      _previousDiskWriteBytes,
      seconds,
    );
    _appendRate(
      _networkReceiveHistory,
      info.totalNetworkReceivedBytes,
      _previousNetworkReceiveBytes,
      seconds,
    );
    _appendRate(
      _networkSendHistory,
      info.totalNetworkSentBytes,
      _previousNetworkSendBytes,
      seconds,
    );
    _previousDiskReadBytes = info.totalDiskReadBytes;
    _previousDiskWriteBytes = info.totalDiskWriteBytes;
    _previousNetworkReceiveBytes = info.totalNetworkReceivedBytes;
    _previousNetworkSendBytes = info.totalNetworkSentBytes;
    _previousSampleMillis = now;
  }

  void _appendRate(
    RingBuffer history,
    int current,
    int? previous,
    double seconds,
  ) {
    if (previous == null || seconds <= 0 || current < previous) {
      history.add(0);
      return;
    }
    history.add((current - previous) / seconds);
  }

  void _refresh() {
    final IEnvironmentClient? client = _client;
    if (client != null) _load(client);
  }

  @override
  void dispose() {
    _pollTimer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
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
          title: reactorText(ReactorText.environmentUnavailable),
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
          message: localizedReactorError(error),
          status: ReactorStatus.critical,
          action: Button.secondary(
            label: reactorText(ReactorText.commonRetry),
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
          description: reactorText(ReactorText.environmentNoRuntimeData),
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
            title: reactorText(ReactorText.environmentRefreshFailed),
            message: localizedReactorError(error),
            status: ReactorStatus.warning,
            action: Button.secondary(
              label: reactorText(ReactorText.commonRetry),
              size: ButtonSize.small,
              onPressed: _refresh,
            ),
          )
        else if (_loading)
          ReactorNotice(
            title: reactorText(ReactorText.environmentRefreshing),
            message: reactorText(ReactorText.environmentRefreshingDescription),
            status: ReactorStatus.info,
          ),
        EnvironmentView(
          info: info,
          diskReadHistory: _diskReadHistory.toList(),
          diskWriteHistory: _diskWriteHistory.toList(),
          networkReceiveHistory: _networkReceiveHistory.toList(),
          networkSendHistory: _networkSendHistory.toList(),
        ),
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
