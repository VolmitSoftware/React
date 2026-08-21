library;

import 'dart:js_interop';
import 'dart:js_interop_unsafe';

import 'package:web/web.dart' as web;

double _jsDouble(JSObject owner, String property) {
  final JSAny? value = owner.getProperty<JSAny?>(property.toJS);
  if (value == null || !value.isA<JSNumber>()) return 0;
  return (value as JSNumber).toDartDouble;
}

void writePaneVariable(String name, double px) {
  try {
    final web.Element? root = web.document.documentElement;
    if (root == null) return;
    (root as web.HTMLElement).style.setProperty(name, '${px.round()}px');
  } catch (_) {}
}

void writePaneAria(String handleId, double width) {
  try {
    web.document
        .getElementById(handleId)
        ?.setAttribute('aria-valuenow', width.round().toString());
  } catch (_) {}
}

double paneViewportWidth() {
  try {
    final double width = _jsDouble(web.window as JSObject, 'innerWidth');
    return width.isFinite && width > 0 ? width : 0;
  } catch (_) {
    return 0;
  }
}

void Function() installPaneSplitter({
  required String handleId,
  required void Function(double edgeX) onDragMove,
  required void Function() onDragEnd,
  required void Function() onReset,
  required void Function(String key, bool shift) onKey,
}) {
  final web.Element? element = web.document.getElementById(handleId);
  if (element == null) return () {};
  final web.HTMLElement handle = element as web.HTMLElement;
  double grabOffset = 0;
  int activePointer = -1;
  final Map<String, JSFunction> listeners = <String, JSFunction>{};

  void bind(
    String type,
    void Function(web.Event event) handler, {
    bool passive = true,
  }) {
    final JSFunction listener = handler.toJS;
    listeners[type] = listener;
    handle.addEventListener(
      type,
      listener,
      web.AddEventListenerOptions(passive: passive),
    );
  }

  void endDrag() {
    if (activePointer < 0) return;
    try {
      handle.releasePointerCapture(activePointer);
    } catch (_) {}
    activePointer = -1;
    web.document.body?.classList.remove('reactor-resizing');
    onDragEnd();
  }

  bind('pointerdown', (web.Event event) {
    if (!event.isA<web.PointerEvent>()) return;
    final web.PointerEvent pointer = event as web.PointerEvent;
    if (pointer.button != 0) return;
    event.preventDefault();
    grabOffset =
        _jsDouble(pointer as JSObject, 'clientX') -
        handle.getBoundingClientRect().x;
    activePointer = pointer.pointerId;
    try {
      handle.setPointerCapture(activePointer);
    } catch (_) {}
    web.document.body?.classList.add('reactor-resizing');
    handle.focus();
  }, passive: false);

  bind('pointermove', (web.Event event) {
    if (activePointer < 0 || !event.isA<web.PointerEvent>()) return;
    event.preventDefault();
    onDragMove(_jsDouble(event as JSObject, 'clientX') - grabOffset);
  }, passive: false);

  bind('pointerup', (web.Event event) => endDrag());
  bind('pointercancel', (web.Event event) => endDrag());
  bind('dblclick', (web.Event event) {
    event.preventDefault();
    onReset();
  }, passive: false);
  bind('keydown', (web.Event event) {
    if (!event.isA<web.KeyboardEvent>()) return;
    final web.KeyboardEvent keyboard = event as web.KeyboardEvent;
    const List<String> handled = <String>[
      'ArrowLeft',
      'ArrowRight',
      'Home',
      'End',
    ];
    if (!handled.contains(keyboard.key)) return;
    event.preventDefault();
    event.stopPropagation();
    onKey(keyboard.key, keyboard.shiftKey);
  }, passive: false);

  return () {
    for (final MapEntry<String, JSFunction> entry in listeners.entries) {
      handle.removeEventListener(entry.key, entry.value);
    }
    listeners.clear();
    web.document.body?.classList.remove('reactor-resizing');
  };
}
