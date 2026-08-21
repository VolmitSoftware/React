library;

import 'fleet_download_stub.dart'
    if (dart.library.js_interop) 'fleet_download_web.dart'
    as platform;

void downloadFleetJson(String content, String filename) =>
    platform.downloadFleetJson(content, filename);
