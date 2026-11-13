/// Console: the server's live log and, with `console:execute`, the command
/// box. The log viewport is the page's one scrolling region; it follows the
/// newest line until the reader scrolls up. Pause holds new lines back
/// without losing them; a dropped stream shows Reconnecting.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component, ListenableBuilder, kIsWeb;
import 'package:universal_web/web.dart' as web;

import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/role_info.dart';
import '../service/react_client.dart';
import '../service/react_exceptions.dart';
import '../state/connection_manager.dart';
import '../state/log_controller.dart';
import '../state/role_scope.dart';
import '../state/server_scope.dart';
import '../state/server_session.dart';
import '../state/server_session_scope.dart';
import '../ui/kit/action_row.dart';
import '../ui/kit/alert.dart';
import '../ui/kit/browser.dart';
import '../ui/kit/menu.dart';
import '../ui/kit/page.dart';
import '../ui/kit/role_gate.dart';
import '../ui/kit/states.dart';
import '../ui/kit/surface.dart';
import '../ui/kit/toggle_choice.dart';
import '../ui/error_text.dart';

/// The level a log line was written at, read from its bracketed prefix
/// (`[12:00:01 WARN]:`, `[ERROR]`).
enum LogLevel { info, warn, error, debug }

final RegExp _levelWord = RegExp(
  r'\b(SEVERE|ERROR|FATAL|WARNING|WARN|INFO|DEBUG|TRACE)\b',
);

/// The level in [line]'s prefix, or null for a line without one (a stack
/// trace or a wrapped message).
LogLevel? logLevelOf(String line) {
  if (!line.startsWith('[')) return null;
  final int end = line.indexOf(']');
  final String prefix = (end < 0 ? line : line.substring(0, end)).toUpperCase();
  final RegExpMatch? match = _levelWord.firstMatch(prefix);
  return switch (match?.group(1)) {
    'SEVERE' || 'ERROR' || 'FATAL' => LogLevel.error,
    'WARNING' || 'WARN' => LogLevel.warn,
    'DEBUG' || 'TRACE' => LogLevel.debug,
    'INFO' => LogLevel.info,
    _ => null,
  };
}

/// [lines] with their levels; a line without a level of its own belongs to
/// the line above it, so a stack trace stays with its error.
List<(String, LogLevel)> levelledLines(List<String> lines) {
  LogLevel current = LogLevel.info;
  return <(String, LogLevel)>[
    for (final String line in lines)
      (line, current = logLevelOf(line) ?? current),
  ];
}

/// The lines that pass the level filter ([level] null shows all) and
/// contain [query], ignoring case.
List<(String, LogLevel)> filterLogLines(
  List<String> lines, {
  LogLevel? level,
  String query = '',
}) {
  final String needle = query.trim().toLowerCase();
  return <(String, LogLevel)>[
    for (final (String, LogLevel) entry in levelledLines(lines))
      if ((level == null || entry.$2 == level) &&
          (needle.isEmpty || entry.$1.toLowerCase().contains(needle)))
        entry,
  ];
}

/// The scrolling log: one line per row, coloured by level.
class LogViewport extends StatelessWidget {
  const LogViewport({
    required this.id,
    required this.lines,
    required this.label,
    this.onScroll,
    super.key,
  });

  final String id;
  final List<(String, LogLevel)> lines;

  /// The log's accessible name.
  final String label;
  final void Function()? onScroll;

  static String _lineClasses(LogLevel level) => switch (level) {
    LogLevel.error => 'reactor-log-line is-error',
    LogLevel.warn => 'reactor-log-line is-warning',
    LogLevel.debug => 'reactor-log-line is-debug',
    LogLevel.info => 'reactor-log-line',
  };

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    return dom.div(
      id: id,
      classes: 'reactor-log',
      attributes: <String, String>{
        'role': 'log',
        'aria-live': 'off',
        'aria-label': label,
        'tabindex': '0',
      },
      events: onScroll == null
          ? null
          : <String, void Function(web.Event)>{
              'scroll': (web.Event _) => onScroll!(),
            },
      <Widget>[
        if (lines.isEmpty)
          EmptyState(message: reactorText(ReactorText.logsNoLines))
        else
          for (final (String, LogLevel) entry in lines)
            dom.div(classes: _lineClasses(entry.$2), <Widget>[
              Component.text(entry.$1),
            ]),
      ],
    );
  }
}

/// The console of the server the page belongs to, from its session: the
/// stream opens while the page shows it and closes a while after it leaves.
class LogsScreen extends StatefulWidget {
  const LogsScreen({super.key});

  @override
  State<LogsScreen> createState() => _LogsScreenState();
}

class _LogsScreenState extends State<LogsScreen> {
  static const String _logId = 'reactor-log';

  LogLevel? _level;
  String _query = '';
  bool _following = true;
  String _command = '';
  bool _sending = false;

  /// The session the view state belongs to; another server starts over.
  String? _session;

  /// A phone: the level filter moves into the toolbar's "..." menu, so
  /// the search box keeps its room.
  bool _compact = false;
  void Function() _stopCompact = () {};

  @override
  void initState() {
    super.initState();
    final (bool compact, void Function() stop) = watchMedia(compactQuery, (
      bool compact,
    ) {
      if (mounted) setState(() => _compact = compact);
    });
    _compact = compact;
    _stopCompact = stop;
  }

  @override
  void dispose() {
    _stopCompact();
    super.dispose();
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final String? session = ServerSessionScope.of(context)?.key;
    if (session == _session) return;
    _session = session;
    _following = true;
    _command = '';
  }

  web.HTMLElement? get _viewport =>
      kIsWeb ? web.document.getElementById(_logId) as web.HTMLElement? : null;

  void _onScroll() {
    final web.HTMLElement? log = _viewport;
    if (log == null) return;
    final bool atEnd = log.scrollHeight - log.scrollTop - log.clientHeight < 24;
    if (atEnd != _following) setState(() => _following = atEnd);
  }

  /// Runs a frame after a build. A scroll up in between turned following
  /// off, and the view stays where the reader put it.
  void _toEnd() {
    if (!_following) return;
    final web.HTMLElement? log = _viewport;
    if (log != null) log.scrollTop = log.scrollHeight.toDouble();
  }

  void _jumpToLatest() {
    setState(() => _following = true);
    afterFrame(_toEnd);
  }

  IConsoleClient? _console(BuildContext context) =>
      ServerSessionScope.of(context)?.clientAs<IConsoleClient>();

  bool _consoleAllowed(BuildContext context) =>
      ServerScope.of(context)?.state == ConnState.live &&
      _console(context) != null &&
      RoleGate.allows(context, RoleNeed.console);

  Future<void> _run() async {
    final IConsoleClient? client = _console(context);
    final String command = _command.trim();
    if (client == null ||
        command.isEmpty ||
        _sending ||
        !_consoleAllowed(context)) {
      return;
    }
    setState(() => _sending = true);
    try {
      final bool dispatched = await client.executeConsole(command);
      if (!mounted) return;
      // The log shows the result; a toast here would cover Run.
      if (dispatched) {
        setState(() => _command = '');
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
      if (mounted) setState(() => _sending = false);
    }
  }

  AppPage _page({required List<Widget> children}) => AppPage(
    title: reactorText(ReactorText.pageConsoleTitle),
    tip: reactorText(ReactorText.logsSubtitle),
    children: children,
  );

  Widget _search() => dom.div(classes: 'reactor-grow', <Widget>[
    TextInput(
      type: TextInputType.search,
      size: ComponentSize.md,
      value: _query,
      placeholder: reactorText(ReactorText.logsSearch),
      attributes: <String, String>{
        'aria-label': reactorText(ReactorText.logsSearch),
      },
      fullWidth: true,
      onChanged: (String value) => setState(() => _query = value),
    ),
  ]);

  ActionRow _toolbar(LogController controller) {
    final bool paused = controller.paused;
    final List<ChoiceOption<LogLevel?>> levels = <ChoiceOption<LogLevel?>>[
      ChoiceOption<LogLevel?>(null, reactorText(ReactorText.commonAll)),
      ChoiceOption<LogLevel?>(
        LogLevel.warn,
        reactorText(ReactorText.logsLevelWarn),
      ),
      ChoiceOption<LogLevel?>(
        LogLevel.error,
        reactorText(ReactorText.logsLevelError),
      ),
    ];
    return ActionRow(
      label: reactorText(ReactorText.pageConsoleTitle),
      leading: <Widget>[
        if (!_compact)
          ToggleChoice<LogLevel?>(
            label: reactorText(ReactorText.logsLevel),
            options: levels,
            value: _level,
            onChanged: (LogLevel? level) => setState(() => _level = level),
          ),
        _search(),
      ],
      more: <AppMenuEntry>[
        if (_compact)
          for (final ChoiceOption<LogLevel?> level in levels)
            AppMenuEntry(
              level.label,
              () => setState(() => _level = level.value),
              icon: level.value == _level ? ArcaneIcon.check() : null,
            ),
      ],
      secondary: <RowAction>[
        // On a phone both are icon buttons (named, with a tooltip), so the
        // search box gets the row.
        RowAction(
          reactorText(paused ? ReactorText.logsResume : ReactorText.logsPause),
          () => controller.setPaused(!paused),
          icon: paused ? ArcaneIcon.play() : ArcaneIcon.pause(),
          iconOnly: _compact,
          pressed: paused,
        ),
        RowAction(
          reactorText(ReactorText.logsClear),
          controller.clear,
          icon: ArcaneIcon.eraser(),
          iconOnly: _compact,
        ),
      ],
    );
  }

  Widget _commandRow(BuildContext context) {
    final bool allowed = _consoleAllowed(context);
    final bool ready = allowed && !_sending && _command.trim().isNotEmpty;
    return ActionRow(
      label: reactorText(ReactorText.consoleCommand),
      leading: <Widget>[
        dom.div(
          classes: 'reactor-grow',
          attributes: const <String, String>{'dir': 'ltr'},
          <Widget>[
            TextInput(
              value: _command,
              type: TextInputType.text,
              placeholder: reactorText(ReactorText.consoleCommand),
              attributes: <String, String>{
                'aria-label': reactorText(ReactorText.consoleCommand),
                'autocomplete': 'off',
                'spellcheck': 'false',
              },
              disabled: !allowed || _sending,
              fullWidth: true,
              onChanged: (String value) => setState(() => _command = value),
              onSubmitted: (String _) => _run(),
            ),
          ],
        ),
      ],
      primary: RowAction(
        reactorText(ReactorText.consoleRun),
        ready ? _run : null,
        loading: _sending,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final ServerSession? session = ServerSessionScope.of(context);
    final RoleInfo? role = RoleScope.of(context)?.role;
    if (role != null && !role.scopes.contains('console:read')) {
      return _page(
        children: <Widget>[
          EmptyState(
            message: reactorText(ReactorText.logsNoAccess),
            description: reactorText(ReactorText.logsNoAccessDescription),
            icon: ArcaneIcon.lock(),
          ),
        ],
      );
    }
    if (session == null || !session.supports<ILogClient>()) {
      return _page(
        children: <Widget>[
          if (ServerScope.of(context)?.state == ConnState.connecting)
            LoadingState(label: reactorText(ReactorText.logsLoadingHistory))
          else
            EmptyState(
              message: reactorText(ReactorText.logsUnavailable),
              description: reactorText(ReactorText.logsLiveRequired),
            ),
        ],
      );
    }
    final LogController controller = session.logs;
    return ListenableBuilder(
      listenable: controller,
      builder: (BuildContext context) => _body(context, controller),
    );
  }

  /// What the log shows instead of lines when none could be read.
  Widget? _failure(LogController controller) {
    final Object? error = controller.error;
    if (error == null || controller.lines.isNotEmpty) return null;
    final ReactErrorKind kind = reactErrorKind(error);
    if (kind == ReactErrorKind.forbidden) {
      return EmptyState(
        message: reactorText(ReactorText.logsNoAccess),
        description: reactorText(ReactorText.logsNoAccessDescription),
        icon: ArcaneIcon.lock(),
      );
    }
    final bool retry =
        kind != ReactErrorKind.revoked &&
        kind != ReactErrorKind.wrongServer &&
        kind != ReactErrorKind.authentication;
    return ErrorState(
      title: reactorText(ReactorText.logsUnavailable),
      message: localizedReactorError(error),
      onRetry: retry ? controller.load : null,
    );
  }

  Widget _body(BuildContext context, LogController controller) {
    final List<String> lines = controller.lines;
    final bool waiting = controller.loading && lines.isEmpty;
    final Widget? failure = waiting ? null : _failure(controller);
    if (_following) afterFrame(_toEnd);
    return _page(
      children: <Widget>[
        dom.div(classes: 'ui-stack', <Widget>[
          _toolbar(controller),
          if (controller.reconnecting)
            AppAlert(
              tone: AlertTone.warning,
              message: reactorText(ReactorText.logsReconnecting),
              live: true,
            ),
          SurfaceScope(
            child: dom.div(classes: 'reactor-log-frame', <Widget>[
              if (waiting)
                LoadingState(label: reactorText(ReactorText.logsLoadingHistory))
              else if (failure != null)
                failure
              else
                LogViewport(
                  id: _logId,
                  label: reactorText(ReactorText.pageConsoleTitle),
                  lines: filterLogLines(lines, level: _level, query: _query),
                  onScroll: _onScroll,
                ),
              if (!_following && !waiting && failure == null)
                dom.div(classes: 'reactor-log-jump', <Widget>[
                  ActionRow(
                    dense: true,
                    secondary: <RowAction>[
                      RowAction(
                        reactorText(ReactorText.logsJumpLatest),
                        _jumpToLatest,
                        icon: ArcaneIcon.arrowDown(),
                      ),
                    ],
                  ),
                ]),
            ]),
          ),
          RoleGate(need: RoleNeed.console, child: _commandRow(context)),
        ]),
      ],
    );
  }
}
