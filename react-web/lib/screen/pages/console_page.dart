/// Console (L4): the live log and, with console:execute, the command box.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';

import '../logs.dart';

class ConsolePage extends StatelessWidget {
  const ConsolePage({super.key});

  @override
  Widget build(BuildContext context) => const LogsScreen();
}
