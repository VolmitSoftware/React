/// One console line. [seq] is the plugin's ordering number; null on plugins
/// that predate sequenced log frames.
class LogLine {
  final int? seq;
  final String text;

  const LogLine(this.text, {this.seq});

  static LogLine? fromJson(Object? raw) {
    if (raw is String) return LogLine(raw);
    if (raw is! Map<String, dynamic>) return null;
    final Object? text = raw['line'] ?? raw['text'] ?? raw['message'];
    if (text is! String) return null;
    final Object? seq = raw['seq'];
    return LogLine(text, seq: seq is num ? seq.toInt() : null);
  }

  static List<LogLine> listFromJson(List<dynamic> raw) => <LogLine>[
    for (final Object? entry in raw) ?LogLine.fromJson(entry),
  ];

  /// `GET /logs` lists contiguous lines whose last one has seq [lastSeq];
  /// gives each line without a seq its number.
  static List<LogLine> withTrailingSeq(List<LogLine> lines, Object? lastSeq) {
    if (lastSeq is! num) return lines;
    final int last = lastSeq.toInt();
    return <LogLine>[
      for (int i = 0; i < lines.length; i++)
        lines[i].seq != null
            ? lines[i]
            : LogLine(lines[i].text, seq: last - (lines.length - 1 - i)),
    ];
  }
}

/// One `/ws/logs` frame: a batch of lines plus how many the server dropped
/// for this session since the previous frame.
class LogBatch {
  final List<LogLine> lines;
  final int dropped;

  const LogBatch(this.lines, {this.dropped = 0});

  /// Parses `{"type":"logs","lines":[...],"dropped":k}` and the older
  /// `{"type":"log","line":"..."}`. Anything else returns null.
  static LogBatch? fromFrame(Map<String, dynamic> json) {
    final Object? type = json['type'];
    final Object? dropped = json['dropped'];
    final int droppedCount = dropped is num ? dropped.toInt() : 0;
    if (type == 'logs') {
      final Object? lines = json['lines'];
      if (lines is! List<dynamic>) return null;
      return LogBatch(LogLine.listFromJson(lines), dropped: droppedCount);
    }
    if (type == 'log') {
      final LogLine? line = LogLine.fromJson(json['line']);
      if (line == null) return null;
      final Object? seq = json['seq'];
      return LogBatch(<LogLine>[
        seq is num ? LogLine(line.text, seq: seq.toInt()) : line,
      ], dropped: droppedCount);
    }
    return null;
  }
}
