import 'package:jaspr/jaspr.dart';

import '../service/react_client.dart';
import '../service/react_log_socket.dart';

class OperateScope extends InheritedComponent {
  final IOperateClient? client;
  final ILogSocket Function()? logSocketFactory;

  const OperateScope({
    required this.client,
    required this.logSocketFactory,
    required super.child,
    super.key,
  });

  static OperateScope? of(BuildContext context) =>
      context.dependOnInheritedComponentOfExactType<OperateScope>();

  @override
  bool updateShouldNotify(OperateScope old) =>
      client != old.client || logSocketFactory != old.logSocketFactory;
}
