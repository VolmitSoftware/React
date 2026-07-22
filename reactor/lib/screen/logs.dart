library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../service/react_client.dart';
import '../localization/reactor_localizations.dart';
import '../service/react_log_socket.dart';
import '../state/log_controller.dart';
import '../state/operate_scope.dart';
import '../ui/reactor_ui.dart';
import '../widget/section_card.dart';

class LogsView extends StatelessWidget {
  final List<String> lines;
  final bool paused;
  final String levelFilter;
  final void Function(bool)? onPause;
  final void Function()? onClear;
  final void Function(String)? onLevelFilter;

  const LogsView({
    required this.lines,
    required this.paused,
    required this.levelFilter,
    required this.onPause,
    required this.onClear,
    required this.onLevelFilter,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    return ReactorPage(
      title: reactorText(ReactorText.logsTitle),
      subtitle: reactorText(ReactorText.logsSubtitle),
      actions: dom.div(
        styles: const dom.Styles(
          raw: <String, String>{
            'display': 'flex',
            'align-items': 'center',
            'gap': '0.5rem',
          },
        ),
        <Widget>[
          Button.secondary(
            label: paused
                ? reactorText(ReactorText.logsResume)
                : reactorText(ReactorText.logsPause),
            size: ButtonSize.small,
            onPressed: () => onPause?.call(!paused),
          ),
          Button.ghost(
            label: reactorText(ReactorText.logsClear),
            size: ButtonSize.small,
            onPressed: () => onClear?.call(),
          ),
        ],
      ),
      children: <Widget>[
        sectionCard(
          label: reactorText(ReactorText.logsStream),
          flush: true,
          trailing: ArcaneSelect(
            label: reactorText(ReactorText.logsLevel),
            value: levelFilter,
            options: const <ArcaneSelectOption>[
              ArcaneSelectOption(label: 'ALL', value: 'ALL'),
              ArcaneSelectOption(label: 'INFO', value: 'INFO'),
              ArcaneSelectOption(label: 'WARN', value: 'WARN'),
              ArcaneSelectOption(label: 'ERROR', value: 'ERROR'),
              ArcaneSelectOption(label: 'DEBUG', value: 'DEBUG'),
            ],
            onChange: (String s) => onLevelFilter?.call(s),
          ),
          child: ArcaneScrollArea.vertical(
            height: '480px',
            child: dom.div(
              styles: const dom.Styles(
                raw: <String, String>{
                  'display': 'flex',
                  'flex-direction': 'column',
                },
              ),
              <Widget>[
                for (final String line in lines)
                  dom.div(
                    styles: const dom.Styles(
                      raw: <String, String>{
                        'font-family': 'monospace',
                        'font-size': '0.8rem',
                        'white-space': 'pre-wrap',
                      },
                    ),
                    <Widget>[Component.text(line)],
                  ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

class LogsScreen extends StatefulWidget {
  const LogsScreen({super.key});

  @override
  State<LogsScreen> createState() => _LogsScreenState();
}

class _LogsScreenState extends State<LogsScreen> {
  ILogClient? _client;
  LogController? _controller;
  bool _started = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final OperateScope? scope = OperateScope.of(context);
    final ILogClient? client = scope?.client;
    final ILogSocket Function()? factory = scope?.logSocketFactory;
    if (client != null && !_started) {
      _started = true;
      _client = client;
      _controller = LogController(
        client,
        socket: factory?.call(),
        onChange: () => setState(() {}),
      );
      _controller!.load();
      _controller!.start();
    }
  }

  @override
  void dispose() {
    _controller?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (_client == null) {
      return ReactorPage(
        title: reactorText(ReactorText.logsTitle),
        subtitle: reactorText(ReactorText.logsSubtitle),
        children: <Widget>[
          sectionCard(
            label: reactorText(ReactorText.logsTitle),
            child: dom.div(
              styles: const dom.Styles(
                raw: <String, String>{
                  'color': 'var(--muted-foreground)',
                  'font-size': '0.875rem',
                },
              ),
              <Widget>[
                Component.text(reactorText(ReactorText.logsLiveRequired)),
              ],
            ),
          ),
        ],
      );
    }

    final LogController controller = _controller!;

    return LogsView(
      lines: controller.visible,
      paused: controller.paused,
      levelFilter: controller.levelFilter,
      onPause: (bool v) => setState(() => controller.setPaused(v)),
      onClear: () => setState(() => controller.clear()),
      onLevelFilter: (String level) =>
          setState(() => controller.setLevelFilter(level)),
    );
  }
}
