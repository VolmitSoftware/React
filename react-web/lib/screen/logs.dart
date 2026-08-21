library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../service/react_client.dart';
import '../localization/reactor_localizations.dart';
import '../model/role_info.dart';
import '../service/react_log_socket.dart';
import '../state/connection_manager.dart';
import '../state/log_controller.dart';
import '../state/operate_scope.dart';
import '../state/role_scope.dart';
import '../state/server_scope.dart';
import '../ui/reactor_ui.dart';

class LogsView extends StatelessWidget {
  final List<String> lines;
  final bool paused;
  final String levelFilter;
  final Widget? notice;
  final void Function(bool)? onPause;
  final void Function()? onClear;
  final void Function(String)? onLevelFilter;
  final String command;
  final bool consoleEnabled;
  final String? consoleUnavailableMessage;
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
    this.notice,
    this.command = '',
    this.consoleEnabled = false,
    this.consoleUnavailableMessage,
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
        ?notice,
        SectionPanel(
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
              ReactorNotice(
                title: 'Console unavailable',
                message:
                    consoleUnavailableMessage ??
                    reactorText(ReactorText.consoleAdminRequired),
                status: ReactorStatus.warning,
              ),
          ]),
        ),
        SectionPanel(
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
          child: lines.isEmpty
              ? ReactorEmptyState(
                  title: 'No log lines',
                  description:
                      'New matching entries will appear here as React streams them.',
                  icon: ArcaneIcon.scrollText(size: IconSize.sm),
                )
              : dom.div(
                  attributes: const <String, String>{
                    'role': 'log',
                    'aria-live': 'polite',
                  },
                  styles: const dom.Styles(
                    raw: <String, String>{
                      'display': 'flex',
                      'min-height': '100%',
                      'flex-direction': 'column',
                      'background': 'var(--reactor-bg)',
                    },
                  ),
                  <Widget>[
                    for (final String line in lines)
                      dom.div(
                        styles: const dom.Styles(
                          raw: <String, String>{
                            'padding': '0.3rem 0.7rem',
                            'border-bottom': '1px solid $kReactorHairline',
                            'font-family': 'var(--font-mono)',
                            'font-size': '0.72rem',
                            'line-height': '1.45',
                            'white-space': 'pre-wrap',
                            'overflow-wrap': 'anywhere',
                          },
                        ),
                        <Widget>[Component.text(line)],
                      ),
                  ],
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
  Object? _error;

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
        onError: (Object error) {
          if (!mounted) return;
          setState(() => _error = error);
        },
      );
      _controller!.load();
      _controller!.start();
    }
  }

  void _retry() {
    final LogController? controller = _controller;
    if (controller == null) return;
    setState(() => _error = null);
    controller.load();
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
    final bool live = ServerScope.of(context)?.state == ConnState.live;
    if (client == null ||
        command.isEmpty ||
        _consolePending ||
        !allowed ||
        !live) {
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
    final ServerScope? server = ServerScope.of(context);
    if (server?.state == ConnState.connecting && _client == null) {
      return _statePage(
        const ReactorLoadingState(label: 'Opening the log stream…'),
      );
    }
    if (_client == null) {
      return _statePage(
        ReactorNotice(
          title: 'Logs unavailable',
          message: reactorText(ReactorText.logsLiveRequired),
          status: ReactorStatus.critical,
        ),
      );
    }

    final LogController controller = _controller!;
    if (controller.loading && controller.lines.isEmpty) {
      return _statePage(
        const ReactorLoadingState(label: 'Loading recent log lines…'),
      );
    }

    final Object? error = _error;
    if (error != null && controller.lines.isEmpty) {
      return _statePage(
        ReactorNotice(
          title: 'Log stream unavailable',
          message: error.toString(),
          status: ReactorStatus.critical,
          action: Button.secondary(
            label: 'Retry',
            size: ButtonSize.small,
            onPressed: _retry,
          ),
        ),
      );
    }

    final RoleInfo? role = RoleScope.of(context)?.role;
    final bool live = server?.state == ConnState.live;
    final bool consoleEnabled =
        live && _consoleClient != null && (role?.canExecuteConsole ?? false);
    final Widget? notice = error != null
        ? ReactorNotice(
            title: 'Log refresh failed',
            message: error.toString(),
            status: ReactorStatus.warning,
            action: Button.secondary(
              label: 'Retry',
              size: ButtonSize.small,
              onPressed: _retry,
            ),
          )
        : controller.loading
        ? const ReactorNotice(
            title: 'Refreshing log history',
            message: 'Existing streamed lines remain visible during refresh.',
            status: ReactorStatus.info,
          )
        : null;

    return LogsView(
      lines: controller.visible,
      paused: controller.paused,
      levelFilter: controller.levelFilter,
      notice: notice,
      onPause: (bool v) => setState(() => controller.setPaused(v)),
      onClear: () => setState(() => controller.clear()),
      onLevelFilter: (String level) =>
          setState(() => controller.setLevelFilter(level)),
      command: _command,
      consoleEnabled: consoleEnabled,
      consoleUnavailableMessage: !live
          ? 'Command execution is disabled until the connection is live.'
          : null,
      consolePending: _consolePending,
      onCommandChanged: (String value) => setState(() => _command = value),
      onExecuteCommand: _executeConsole,
    );
  }

  Widget _statePage(Widget state) {
    return ReactorPage(
      title: reactorText(ReactorText.logsTitle),
      subtitle: reactorText(ReactorText.logsSubtitle),
      children: <Widget>[state],
    );
  }
}
