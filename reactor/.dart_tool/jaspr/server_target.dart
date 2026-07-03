  import 'package:reactor/main.server.dart' as m;
  import 'package:hotreloader/hotreloader.dart';
      
  void main(List<String> args) async {
    final mainFunc = m.main as dynamic;
    final mainCall = mainFunc is dynamic Function(List<String>) ? () => mainFunc(args) : () => mainFunc();

    try {
      await HotReloader.create(
        debounceInterval: Duration.zero,
        onAfterReload: (ctx) => mainCall(),
      );
      print('[INFO] Server hot reload is enabled.');
    } on StateError catch (e) {
      if (e.message.contains('VM service not available')) {
        print('[WARNING] Server hot reload not enabled. Run with --enable-vm-service to enable hot reload.');
      } else {
        rethrow;
      }
    }
    
    mainCall();
  }
