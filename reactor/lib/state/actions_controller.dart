import '../model/action_descriptor.dart';
import '../service/react_client.dart';

class ActionExecution {
  final String actionId;
  final String ticketId;
  final String status;
  final DateTime at;

  const ActionExecution({
    required this.actionId,
    required this.ticketId,
    required this.status,
    required this.at,
  });
}

class ActionsController {
  final IActionClient _client;
  void Function()? onChange;
  void Function(Object error)? onError;

  List<ActionDescriptor> _actions = const <ActionDescriptor>[];
  bool _loading = false;
  Object? _error;
  List<ActionExecution> _recent = <ActionExecution>[];

  ActionsController(
    this._client, {
    this.onChange,
    this.onError,
  });

  List<ActionDescriptor> get actions => _actions;
  bool get loading => _loading;
  Object? get error => _error;
  List<ActionExecution> get recent =>
      List<ActionExecution>.unmodifiable(_recent);

  void _notify() => onChange?.call();

  Future<void> load() async {
    _loading = true;
    _notify();
    try {
      _actions = await _client.actions();
      _error = null;
    } catch (e) {
      _error = e;
      onError?.call(e);
    } finally {
      _loading = false;
      _notify();
    }
  }

  Future<void> execute(
    String id,
    Map<String, Object?> params,
    bool confirm,
  ) async {
    try {
      final ActionTicket ticket = await _client.executeAction(
        id,
        params: params,
        confirm: confirm,
      );
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
        _recent = _recent.sublist(0, 20);
      }
      _notify();
    } catch (e) {
      onError?.call(e);
    }
  }
}
