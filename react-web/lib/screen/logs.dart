library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../service/react_client.dart';
import '../localization/reactor_locale.dart';
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
    dependOnReactorLocale(context);
    return ReactorPage(
      title: reactorText(ReactorText.logsTitle),
      subtitle: reactorText(ReactorText.logsSubtitle),
      children: <Widget>[
        ?notice,
        SectionPanel(
          label: reactorText(ReactorText.consoleTitle),
          description: reactorText(ReactorText.consoleDescription),
          flush: true,
          trailing: ArcaneSelect(
            label: reactorText(ReactorText.logsLevel),
            value: levelFilter,
            options: <ArcaneSelectOption>[
              ArcaneSelectOption(
                label: reactorText(ReactorText.logsAllLevels),
                value: 'ALL',
              ),
              const ArcaneSelectOption(label: 'INFO', value: 'INFO'),
              const ArcaneSelectOption(label: 'WARN', value: 'WARN'),
              const ArcaneSelectOption(label: 'ERROR', value: 'ERROR'),
              const ArcaneSelectOption(label: 'DEBUG', value: 'DEBUG'),
            ],
            onChange: (String s) => onLevelFilter?.call(s),
          ),
          child: dom.div(classes: 'reactor-terminal', <Widget>[
            dom.div(classes: 'reactor-terminal-toolbar', <Widget>[
              dom.div(classes: 'reactor-terminal-state', <Widget>[
                dom.span(
                  classes: 'reactor-terminal-live${paused ? ' is-paused' : ''}',
                  const <Widget>[],
                ),
                Component.text(
                  paused
                      ? reactorText(ReactorText.logsOutputPaused)
                      : reactorText(
                          ReactorText.logsStreamCount,
                          <String, Object?>{'count': lines.length},
                        ),
                ),
              ]),
              dom.div(classes: 'reactor-terminal-actions', <Widget>[
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
              ]),
            ]),
            dom.div(classes: 'reactor-terminal-output', <Widget>[
              if (lines.isEmpty)
                ReactorEmptyState(
                  title: reactorText(ReactorText.logsNoLines),
                  description: reactorText(ReactorText.logsNoLinesDescription),
                  icon: ArcaneIcon.scrollText(size: IconSize.sm),
                )
              else
                dom.div(
                  attributes: const <String, String>{
                    'role': 'log',
                    'aria-live': 'off',
                  },
                  classes: 'reactor-terminal-lines',
                  <Widget>[
                    for (final String line in lines)
                      dom.div(classes: _lineClasses(line), <Widget>[
                        Component.text(line),
                      ]),
                  ],
                ),
            ]),
            dom.div(classes: 'reactor-console', <Widget>[
              dom.span(classes: 'reactor-console-prompt', <Widget>[
                Component.text('>'),
              ]),
              dom.div(classes: 'reactor-console-input', <Widget>[
                TextInput(
                  value: command,
                  type: TextInputType.text,
                  attributes: const <String, String>{'dir': 'ltr'},
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
              dom.div(classes: 'reactor-console-disabled', <Widget>[
                Component.text(
                  consoleUnavailableMessage ??
                      reactorText(ReactorText.consoleAdminRequired),
                ),
              ]),
          ]),
        ),
      ],
    );
  }

  String _lineClasses(String line) {
    final String normalized = line.toUpperCase();
    if (normalized.contains('[ERROR]') || normalized.contains('[SEVERE]')) {
      return 'reactor-terminal-line is-error';
    }
    if (normalized.contains('[WARN]')) {
      return 'reactor-terminal-line is-warning';
    }
    if (normalized.contains('[DEBUG]') || normalized.contains('[TRACE]')) {
      return 'reactor-terminal-line is-debug';
    }
    return 'reactor-terminal-line';
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
        description: localizedReactorError(error),
      );
    } finally {
      if (mounted) setState(() => _consolePending = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final ServerScope? server = ServerScope.of(context);
    if (server?.state == ConnState.connecting && _client == null) {
      return _statePage(
        ReactorLoadingState(label: reactorText(ReactorText.logsOpening)),
      );
    }
    if (_client == null) {
      return _statePage(
        ReactorNotice(
          title: reactorText(ReactorText.logsUnavailable),
          message: reactorText(ReactorText.logsLiveRequired),
          status: ReactorStatus.critical,
        ),
      );
    }

    final LogController controller = _controller!;
    if (controller.loading && controller.lines.isEmpty) {
      return _statePage(
        ReactorLoadingState(label: reactorText(ReactorText.logsLoadingHistory)),
      );
    }

    final Object? error = _error;
    if (error != null && controller.lines.isEmpty) {
      return _statePage(
        ReactorNotice(
          title: reactorText(ReactorText.logsStreamUnavailable),
          message: localizedReactorError(error),
          status: ReactorStatus.critical,
          action: Button.secondary(
            label: reactorText(ReactorText.commonRetry),
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
            title: reactorText(ReactorText.logsRefreshFailed),
            message: localizedReactorError(error),
            status: ReactorStatus.warning,
            action: Button.secondary(
              label: reactorText(ReactorText.commonRetry),
              size: ButtonSize.small,
              onPressed: _retry,
            ),
          )
        : controller.loading
        ? ReactorNotice(
            title: reactorText(ReactorText.logsRefreshingHistory),
            message: reactorText(ReactorText.logsRefreshingHistoryDescription),
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
          ? reactorText(ReactorText.logsCommandDisabled)
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
