import 'dart:js_interop';
import 'package:web/web.dart' as web;

class PaintScheduler {
  final void Function() paint;
  late final JSFunction _visibilityListener;
  int? _frame;
  bool _dirty = false;
  bool _disposed = false;

  PaintScheduler(this.paint) {
    _visibilityListener = ((web.Event _) {
      if (_dirty) schedule();
    }).toJS;
    web.document.addEventListener('visibilitychange', _visibilityListener);
  }

  void schedule() {
    if (_disposed) return;
    _dirty = true;
    if (web.document.hidden || _frame != null) return;
    _frame = web.window.requestAnimationFrame(
      ((double _) {
        _frame = null;
        if (_disposed || web.document.hidden) return;
        _dirty = false;
        paint();
      }).toJS,
    );
  }

  void dispose() {
    _disposed = true;
    final int? frame = _frame;
    if (frame != null) web.window.cancelAnimationFrame(frame);
    _frame = null;
    web.document.removeEventListener('visibilitychange', _visibilityListener);
  }
}
