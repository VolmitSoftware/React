import 'dart:async';

class PaintScheduler {
  final void Function() paint;
  Timer? _timer;
  bool _disposed = false;

  PaintScheduler(this.paint);

  void schedule() {
    if (_disposed || _timer != null) return;
    _timer = Timer(Duration.zero, () {
      _timer = null;
      if (!_disposed) paint();
    });
  }

  void dispose() {
    _disposed = true;
    _timer?.cancel();
    _timer = null;
  }
}
