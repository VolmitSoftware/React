library;

import 'dart:js_interop';

import 'package:web/web.dart' as web;

void pickFleetImportFile(void Function(String) onPicked) {
  final web.HTMLInputElement input = web.HTMLInputElement()
    ..type = 'file'
    ..accept = '.json,application/json';

  input.addEventListener(
    'change',
    ((web.Event _) {
      final web.FileList? files = input.files;
      if (files == null || files.length == 0) return;
      final web.File? file = files.item(0);
      if (file == null) return;
      final web.FileReader reader = web.FileReader();
      reader.addEventListener(
        'load',
        ((web.Event e) {
          final JSAny? result = reader.result;
          if (result == null) return;
          final Object? raw = result.dartify();
          if (raw is! String) return;
          onPicked(raw);
        }).toJS,
      );
      reader.readAsText(file);
    }).toJS,
  );

  input.click();
}
