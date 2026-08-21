library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../service/react_client.dart';
import '../localization/reactor_localizations.dart';
import '../model/role_info.dart';
import '../service/react_log_socket.dart';
import '../state/log_controller.dart';
import '../state/operate_scope.dart';
import '../state/role_scope.dart';
import '../ui/reactor_ui.dart';
import '../widget/section_card.dart';

class LogsView extends StatelessWidget {
  final List<String> lines;
  final bool paused;
  final String levelFilter;
  final void Function(bool)? onPause;
  final void Function()? onClear;
  final void Function(String)? onLevelFilter;
  final String command;
  final bool consoleEnabled;
  final bool consolePending;
  final void Function(String)? onCommandChanged;
  final void Function()? onExecuteCommand;

  const LogsView({
    required this.lines,
    required this.paused,
    required this.levelFilter,
    required this.onPause,
    required this.onClear,
    required this.onLevelFilter,
    this.command = '',
    this.consoleEnabled = false,
    this.consolePending = false,
    this.onCommandChanged,
    this.onExecuteCommand,
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
          label: reactorText(ReactorText.consoleTitle),
          description: reactorText(ReactorText.consoleDescription),
          child: dom.div(classes: 'reactor-console', <Widget>[
            dom.div(classes: 'reactor-console-command', <Widget>[
              dom.div(classes: 'reactor-console-input', <Widget>[
                TextInput(
                  value: command,
                  type: TextInputType.text,
                  placeholder: reactorText(ReactorText.consolePlaceholder),
                  disabled: !consoleEnabled || consolePending,
                  onChange: consoleEnabled && !consolePending
                      ? onCommandChanged
                      : null,
                  onSubmit:
                      consoleEnabled &&
                          !consolePending &&
                          command.trim().isNotEmpty
                      ? (String value) => onExecuteCommand?.call()
                      : null,
                  fullWidth: true,
                ),
              ]),
              Button.primary(
                label: consolePending
                    ? reactorText(ReactorText.consoleRunning)
                    : reactorText(ReactorText.consoleRun),
                disabled:
                    !consoleEnabled || consolePending || command.trim().isEmpty,
                onPressed:
                    !consoleEnabled || consolePending || command.trim().isEmpty
                    ? null
                    : () => onExecuteCommand?.call(),
              ),
            ]),
            if (!consoleEnabled)
              dom.div(classes: 'reactor-console-note', <Widget>[
                Component.text(reactorText(ReactorText.consoleAdminRequired)),
              ]),
          ]),
        ),
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
  IConsoleClient? _consoleClient;
  LogController? _controller;
  bool _started = false;
  bool _consolePending = false;
  String _command = '';

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final OperateScope? scope = OperateScope.of(context);
    final ILogClient? client = scope?.client;
    _consoleClient = scope?.consoleClient;
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

  Future<void> _executeConsole() async {
    final IConsoleClient? client = _consoleClient;
    final String command = _command.trim();
    final RoleInfo? role = RoleScope.of(context)?.role;
    final bool allowed = role?.canExecuteConsole ?? false;
    if (client == null || command.isEmpty || _consolePending || !allowed) {
      return;
    }

    setState(() => _consolePending = true);
    try {
      final bool dispatched = await client.executeConsole(command);
      if (!mounted) return;
      if (dispatched) {
        setState(() => _command = '');
        ArcaneSonner.success(reactorText(ReactorText.consoleDispatched));
      } else {
        ArcaneSonner.error(reactorText(ReactorText.consoleRejected));
      }
    } on Object catch (error) {
      if (!mounted) return;
      ArcaneSonner.error(
        reactorText(ReactorText.consoleFailed),
        description: error.toString(),
      );
    } finally {
      if (mounted) setState(() => _consolePending = false);
    }
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
    final RoleInfo? role = RoleScope.of(context)?.role;
    final bool consoleEnabled =
        _consoleClient != null && (role?.canExecuteConsole ?? false);

    return LogsView(
      lines: controller.visible,
      paused: controller.paused,
      levelFilter: controller.levelFilter,
      onPause: (bool v) => setState(() => controller.setPaused(v)),
      onClear: () => setState(() => controller.clear()),
      onLevelFilter: (String level) =>
          setState(() => controller.setLevelFilter(level)),
      command: _command,
      consoleEnabled: consoleEnabled,
      consolePending: _consolePending,
      onCommandChanged: (String value) => setState(() => _command = value),
      onExecuteCommand: _executeConsole,
    );
  }
}
