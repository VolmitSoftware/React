/// A question before something that loses work or writes to a server: on
/// shadcn's dialog, with Cancel and one confirm button (red when it
/// destroys). Use [ConfirmDialog] where a widget owns the open state, or
/// [askToConfirm] for a `Future<bool>` from a handler; the shell mounts the
/// [ConfirmHost] that shows those.
library;

import 'dart:async';

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../../localization/reactor_localizations.dart';
import 'action_row.dart';
import 'dialog.dart';

class ConfirmDialog extends StatelessWidget {
  const ConfirmDialog({
    required this.title,
    required this.confirmLabel,
    required this.onConfirm,
    required this.onCancel,
    this.message,
    this.destructive = false,
    this.open = true,
    super.key,
  });

  /// The question ("Remove Skyblock?").
  final String title;

  /// What happens, in one sentence.
  final String? message;

  /// The verb on the button ("Remove server").
  final String confirmLabel;
  final bool destructive;
  final bool open;
  final void Function() onConfirm;
  final void Function() onCancel;

  @override
  Widget build(BuildContext context) => AppDialog(
    open: open,
    alert: true,
    title: title,
    onClose: onCancel,
    footer: DialogFooter(
      cancel: RowAction(reactorText(ReactorText.commonCancel), onCancel),
      primary: RowAction(
        confirmLabel,
        onConfirm,
        destructive: destructive,
        attributes: const <String, String>{'data-confirm': ''},
      ),
    ),
    children: <Widget>[
      if (message != null)
        dom.p(classes: 'ui-confirm-message', <Widget>[
          Component.text(message!),
        ]),
    ],
  );
}

class _Request {
  _Request(this.title, this.message, this.confirmLabel, this.destructive);

  final String title;
  final String? message;
  final String confirmLabel;
  final bool destructive;
  final Completer<bool> answer = Completer<bool>();
}

final List<_Request> _waiting = <_Request>[];
final List<void Function()> _hosts = <void Function()>[];

/// Tells every host the queue changed. A host that throws (its page is
/// gone) is dropped, so it cannot keep the others from hearing it.
void _notifyHosts() {
  for (final void Function() host in List<void Function()>.of(_hosts)) {
    try {
      host();
    } on Object {
      _hosts.remove(host);
    }
  }
}

/// Asks the question in a dialog; true when confirmed. Needs a mounted
/// [ConfirmHost] (the shell has one).
Future<bool> askToConfirm({
  required String title,
  required String confirmLabel,
  String? message,
  bool destructive = false,
}) => confirmTicket(
  title: title,
  confirmLabel: confirmLabel,
  message: message,
  destructive: destructive,
).answer;

/// A question [askToConfirm] shows.
class ConfirmTicket {
  ConfirmTicket._(this._request);

  final _Request _request;

  /// True when confirmed; false when cancelled or taken back.
  Future<bool> get answer => _request.answer.future;
}

/// [askToConfirm] with its ticket. The dialog shows at the shell's
/// [ConfirmHost], above any sheet.
ConfirmTicket confirmTicket({
  required String title,
  required String confirmLabel,
  String? message,
  bool destructive = false,
}) {
  final _Request request = _Request(title, message, confirmLabel, destructive);
  if (_hosts.isEmpty) {
    request.answer.complete(false);
  } else {
    _waiting.add(request);
    _notifyHosts();
  }
  return ConfirmTicket._(request);
}

/// Shows the questions [askToConfirm] queues, one at a time. Only a host
/// in the browser takes questions; one rendered on the server cannot show
/// them.
class ConfirmHost extends StatefulWidget {
  const ConfirmHost({super.key});

  @override
  State<ConfirmHost> createState() => _ConfirmHostState();
}

class _ConfirmHostState extends State<ConfirmHost> {
  void _changed() {
    if (mounted) setState(() {});
  }

  @override
  void initState() {
    super.initState();
    if (context.binding.isClient) _hosts.add(_changed);
  }

  @override
  void dispose() {
    _hosts.remove(_changed);
    super.dispose();
  }

  void _answer(bool yes) {
    if (_waiting.isEmpty) return;
    final _Request request = _waiting.removeAt(0);
    if (!request.answer.isCompleted) request.answer.complete(yes);
    setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    if (_waiting.isEmpty) return const Component.fragment(<Widget>[]);
    final _Request request = _waiting.first;
    return ConfirmDialog(
      key: ValueKey<int>(identityHashCode(request)),
      title: request.title,
      message: request.message,
      confirmLabel: request.confirmLabel,
      destructive: request.destructive,
      onConfirm: () => _answer(true),
      onCancel: () => _answer(false),
    );
  }
}
