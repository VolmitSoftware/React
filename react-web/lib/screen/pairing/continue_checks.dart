/// What the start page's Continue list knows of each server between its
/// summaries, and when to ask again. A dropped live connection keeps the
/// last counts, dimmed, and the row says Checking until the next summary
/// answers or fails; one failed summary (both paths tried, short timeout)
/// says Not reachable. A summary without players or TPS, from a server that
/// just started, keeps the counts from before, dimmed, until it has them.
library;

import '../../model/server_summary.dart';
import '../../state/connection_manager.dart';

/// Where a row's server stands.
enum ServerReach { checking, online, unreachable, ended }

/// The reach to show: the live connection says Online as soon as it is
/// live (after a restart), else the summary's answer.
ServerReach reachOf({
  required bool ended,
  required ConnState live,
  required ServerReach? answered,
}) {
  if (ended) return ServerReach.ended;
  if (live == ConnState.live) return ServerReach.online;
  return answered ?? ServerReach.checking;
}

/// One server's row between summaries.
class RowCheck {
  /// The counts shown: the last summary that had them.
  ServerSummary? summary;

  /// The last summary's answer; null while the row is checked again.
  ServerReach? answered;

  /// [summary] is from before the last drop or answer, shown dimmed.
  bool stale = false;
  DateTime? staleSince;
  DateTime? droppedAt;
}

class ContinueChecks {
  ContinueChecks({DateTime Function()? clock}) : _clock = clock ?? DateTime.now;

  /// An answering server is asked again after this.
  static const Duration checkEvery = Duration(seconds: 5);

  /// A server that did not answer, or answers while its live connection is
  /// down, is asked again after this, so a restart or a stop shows at once.
  static const Duration retryEvery = Duration(milliseconds: 250);

  /// Counts kept from before are asked for again after this.
  static const Duration staleEvery = Duration(seconds: 1);

  /// How long a summary may take on both paths before the server counts as
  /// not reachable.
  static const Duration timeout = Duration(milliseconds: 1500);

  /// How long after a drop an answering server is asked at [retryEvery].
  static const Duration dropWindow = Duration(seconds: 10);

  /// How long counts from before stand in for missing ones.
  static const Duration staleWindow = Duration(seconds: 30);

  final DateTime Function() _clock;
  final Map<String, RowCheck> _rows = <String, RowCheck>{};

  RowCheck of(String id) => _rows.putIfAbsent(id, RowCheck.new);

  void forget(String id) => _rows.remove(id);

  /// The live connection dropped.
  void dropped(String id) {
    final RowCheck row = of(id);
    row.answered = null;
    row.droppedAt = _clock();
    if (row.summary != null && !row.stale) {
      row.stale = true;
      row.staleSince = _clock();
    }
  }

  /// A summary answered.
  void answered(String id, ServerSummary summary) {
    final RowCheck row = of(id);
    row.answered = ServerReach.online;
    final bool complete = summary.players != null && summary.tps != null;
    final DateTime? since = row.staleSince;
    final bool keepOld =
        !complete &&
        row.summary != null &&
        (since == null || _clock().difference(since) < staleWindow);
    if (keepOld) {
      if (!row.stale) {
        row.stale = true;
        row.staleSince = _clock();
      }
      return;
    }
    row.summary = summary;
    row.stale = false;
    row.staleSince = null;
  }

  /// A summary failed; [ended] when the server refused the token.
  void failed(String id, {bool ended = false}) {
    of(id).answered = ended ? ServerReach.ended : ServerReach.unreachable;
  }

  /// When to ask again; null when the pairing ended.
  Duration? next(String id, {required bool live}) {
    final RowCheck row = of(id);
    return switch (row.answered) {
      ServerReach.ended => null,
      ServerReach.unreachable || ServerReach.checking || null => retryEvery,
      ServerReach.online when row.stale => staleEvery,
      ServerReach.online
          when !live &&
              row.droppedAt != null &&
              _clock().difference(row.droppedAt!) < dropWindow =>
        retryEvery,
      ServerReach.online => checkEvery,
    };
  }
}
