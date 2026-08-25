library;

import 'dart:js_interop';

import 'package:web/web.dart' as web;

Future<bool> writeClipboardText(String value) async {
  try {
    await web.window.navigator.clipboard.writeText(value).toDart;
    return true;
  } on Object {
    return false;
  }
}
