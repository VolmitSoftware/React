library;

import 'dart:js_interop';
import 'dart:js_interop_unsafe';

import 'package:web/web.dart' as web;

double _eventNumber(JSObject event, String property) {
  final JSAny? value = event.getProperty<JSAny?>(property.toJS);
  if (value == null || !value.isA<JSNumber>()) return 0;
  return (value as JSNumber).toDartDouble;
}

void Function() installHeatmapInteraction({
  required String elementId,
  required void Function(bool zoomIn) onZoom,
  required void Function(int horizontal, int vertical) onPan,
  required void Function(String key) onKey,
}) {
  final web.Element? element = web.document.getElementById(elementId);
  if (element == null || !element.isA<web.HTMLElement>()) return () {};
  final web.HTMLElement plane = element as web.HTMLElement;
  final Map<String, JSFunction> listeners = <String, JSFunction>{};
  int activePointer = -1;
  double startX = 0;
  double startY = 0;
  int lastWheelAt = 0;

  void bind(
    String type,
    void Function(web.Event event) handler, {
    bool passive = true,
  }) {
    final JSFunction listener = handler.toJS;
    listeners[type] = listener;
    plane.addEventListener(
      type,
      listener,
      web.AddEventListenerOptions(passive: passive),
    );
  }

  void finishDrag(web.Event event) {
    if (activePointer < 0 || !event.isA<web.PointerEvent>()) return;
    final web.PointerEvent pointer = event as web.PointerEvent;
    final double deltaX = _eventNumber(pointer as JSObject, 'clientX') - startX;
    final double deltaY = _eventNumber(pointer as JSObject, 'clientY') - startY;
    try {
      plane.releasePointerCapture(activePointer);
    } catch (_) {}
    activePointer = -1;
    plane.classList.remove('is-dragging');
    if (deltaX.abs() < 12 && deltaY.abs() < 12) return;
    event.preventDefault();
    if (deltaX.abs() >= deltaY.abs()) {
      onPan(deltaX > 0 ? -1 : 1, 0);
    } else {
      onPan(0, deltaY > 0 ? -1 : 1);
    }
  }

  bind('wheel', (web.Event event) {
    if (!event.isA<web.WheelEvent>()) return;
    final web.WheelEvent wheel = event as web.WheelEvent;
    final int now = DateTime.now().millisecondsSinceEpoch;
    event.preventDefault();
    if (now - lastWheelAt < 180) return;
    lastWheelAt = now;
    onZoom(wheel.deltaY < 0);
  }, passive: false);

  bind('pointerdown', (web.Event event) {
    if (!event.isA<web.PointerEvent>()) return;
    final web.PointerEvent pointer = event as web.PointerEvent;
    if (pointer.button != 0) return;
    activePointer = pointer.pointerId;
    startX = _eventNumber(pointer as JSObject, 'clientX');
    startY = _eventNumber(pointer as JSObject, 'clientY');
    try {
      plane.setPointerCapture(activePointer);
    } catch (_) {}
    plane.classList.add('is-dragging');
    plane.focus();
  });

  bind('pointerup', finishDrag, passive: false);
  bind('pointercancel', finishDrag, passive: false);
  bind('keydown', (web.Event event) {
    if (!event.isA<web.KeyboardEvent>()) return;
    final web.KeyboardEvent keyboard = event as web.KeyboardEvent;
    const List<String> handled = <String>[
      'ArrowLeft',
      'ArrowRight',
      'ArrowUp',
      'ArrowDown',
      'Home',
    ];
    if (!handled.contains(keyboard.key)) return;
    event.preventDefault();
    event.stopPropagation();
    onKey(keyboard.key);
  }, passive: false);

  return () {
    for (final MapEntry<String, JSFunction> entry in listeners.entries) {
      plane.removeEventListener(entry.key, entry.value);
    }
    listeners.clear();
    plane.classList.remove('is-dragging');
  };
}
