/// Alerts (L1): every open alert of the server group. The screen draws the
/// whole page, since its filters sit in the page header.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';

import '../alerts_inbox.dart';

class AlertsPage extends StatelessWidget {
  const AlertsPage({super.key});

  @override
  Widget build(BuildContext context) => const AlertsInboxScreen();
}
