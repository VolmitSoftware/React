library;

import 'fleet_import_picker_stub.dart'
    if (dart.library.js_interop) 'fleet_import_picker_web.dart'
    as platform;

void pickFleetImportFile(void Function(String) onPicked) =>
    platform.pickFleetImportFile(onPicked);
