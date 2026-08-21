library;

void writePaneVariable(String name, double px) {}

void writePaneAria(String handleId, double width) {}

double paneViewportWidth() => 0;

void Function() installPaneSplitter({
  required String handleId,
  required void Function(double edgeX) onDragMove,
  required void Function() onDragEnd,
  required void Function() onReset,
  required void Function(String key, bool shift) onKey,
}) => () {};
