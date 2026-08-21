library;

import 'dart:js_interop';

import 'package:web/web.dart' as web;

void downloadFleetJson(String content, String filename) {
  final web.Blob blob = web.Blob(
    <JSAny>[content.toJS].toJS,
    web.BlobPropertyBag(type: 'application/json'),
  );
  final String url = web.URL.createObjectURL(blob);
  final web.HTMLAnchorElement a = web.HTMLAnchorElement()
    ..href = url
    ..download = filename;
  a.click();
  web.URL.revokeObjectURL(url);
}
