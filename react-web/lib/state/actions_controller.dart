import 'dart:async';

import '../model/action_descriptor.dart';
import '../model/server_revisions.dart';
import '../service/react_client.dart';
import '../service/react_exceptions.dart';
import 'live_controller.dart';

class ActionExecution {
  final String actionId;
  final String ticketId;
  final String status;
  final DateTime at;

  /// Outcome from ticket polling. Stays [ActionTicketState.queued] on
  /// plugins without the ticket route.
  final ActionTicketState state;
  final String message;

  const ActionExecution({
    required this.actionId,
    required this.ticketId,
    required this.status,
    required this.at,
    this.state = ActionTicketState.queued,
    this.message = '',
  });

  ActionExecution withStatus(ActionTicketStatus ticket) => ActionExecution(
    actionId: actionId,
    ticketId: ticketId,
    status: ticket.state.name,
    at: at,
    state: ticket.state,
    message: ticket.message,
  );
}

/// Actions of one server and the recent runs started from this page, each
/// polled until its ticket reports done or failed.
class ActionsController extends LiveController {
  final IActionClient _client;

  /// Poll delays per attempt; the last one repeats until [ticketTimeout].
  final List<Duration> ticketPollDelays;
  final Duration ticketTimeout;

  List<ActionDescriptor> _actions = const <ActionDescriptor>[];
  List<ActionExecution> _recent = <ActionExecution>[];
  final Map<String, Timer> _polls = <String, Timer>{};

  ActionsController(
    IActionClient client, {
    super.serverId,
    super.revisions,
    super.refetchDelay,
    this.ticketPollDelays = const <Duration>[
      Duration(milliseconds: 500),
      Duration(seconds: 1),
      Duration(seconds: 2),
    ],
    this.ticketTimeout = const Duration(minutes: 2),
  }) : _client = client,
       super(kind: RevisionKind.actions);

  List<ActionDescriptor> get actions => _actions;
  List<ActionExecution> get recent =>
      List<ActionExecution>.unmodifiable(_recent);

  @override
  Future<void> fetch() async {
    final List<ActionDescriptor> fresh = await _client.actions();
    if (!disposed) _actions = fresh;
  }

  /// Queues the action. Completes with the error, if any.
  Future<Object?> execute(
    String id,
    Map<String, Object?> params,
    bool confirm,
  ) async {
    try {
      ensureOpen();
      final ActionTicket ticket = await _client.executeAction(
        id,
        params: params,
        confirm: confirm,
      );
      if (disposed) return null;
      _recent.insert(
        0,
        ActionExecution(
          actionId: id,
          ticketId: ticket.ticketId,
          status: ticket.status,
          at: DateTime.now(),
        ),
      );
      if (_recent.length > 20) {
        for (final ActionExecution dropped in _recent.sublist(20)) {
          _polls.remove(dropped.ticketId)?.cancel();
        }
        _recent = _recent.sublist(0, 20);
      }
      notify();
      _pollTicket(ticket.ticketId, 0, DateTime.now());
      return null;
    } on Object catch (e) {
      return e;
    }
  }

  void _pollTicket(String ticketId, int attempt, DateTime started) {
    final IActionClient client = _client;
    if (client is! IActionTicketClient || disposed) return;
    final Duration delay =
        ticketPollDelays[attempt.clamp(0, ticketPollDelays.length - 1)];
    _polls[ticketId] = Timer(delay, () async {
      _polls.remove(ticketId);
      if (disposed) return;
      try {
        final ActionTicketStatus status = await (client as IActionTicketClient)
            .actionTicket(ticketId);
        if (disposed) return;
        _update(ticketId, status);
        if (!status.state.finished &&
            DateTime.now().difference(started) < ticketTimeout) {
          _pollTicket(ticketId, attempt + 1, started);
        }
      } on ReactNotFound {
        return;
      } on ReactForbidden {
        return;
      } on Object {
        if (DateTime.now().difference(started) < ticketTimeout) {
          _pollTicket(ticketId, attempt + 1, started);
        }
      }
    });
  }

  void _update(String ticketId, ActionTicketStatus status) {
    final int index = _recent.indexWhere(
      (ActionExecution run) => run.ticketId == ticketId,
    );
    if (index < 0) return;
    _recent[index] = _recent[index].withStatus(status);
    notify();
  }

  @override
  void dispose() {
    for (final Timer timer in _polls.values) {
      timer.cancel();
    }
    _polls.clear();
    super.dispose();
  }
}
