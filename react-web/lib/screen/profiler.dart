/// Profiler > Live, and the profiler part of a plugin's or mod's page: the
/// sampler card, then the profile it has gathered, refreshed at the pace
/// the viewer picked.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/jaspr.dart' show ListenableBuilder;

import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/profiler.dart';
import '../service/react_client.dart';
import '../service/react_exceptions.dart';
import '../state/connection_manager.dart';
import '../state/profiler_view.dart';
import '../state/server_scope.dart';
import '../state/server_session.dart';
import '../state/server_session_scope.dart';
import '../ui/error_text.dart';
import '../ui/kit/page.dart';
import '../ui/kit/states.dart';
import 'profile_view.dart';
import 'profiler_controls.dart';

class ProfilerScreen extends StatefulWidget {
  const ProfilerScreen({this.owner, this.snapshotLabel, super.key});

  /// The owner to narrow to; the whole server when null.
  final String? owner;

  /// The label of a snapshot taken here.
  final String? snapshotLabel;

  @override
  State<ProfilerScreen> createState() => _ProfilerScreenState();
}

class _ProfilerScreenState extends State<ProfilerScreen> {
  IProfilerClient? _client;
  ProfilerViewController? _controller;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final ServerSession? session = ServerSessionScope.of(context);
    final IProfilerClient? client = session?.clientAs<IProfilerClient>();
    if (identical(client, _client)) return;
    _controller?.dispose();
    _client = client;
    _controller = client == null
        ? null
        : ProfilerViewController(
            client,
            owner: component.owner,
            storage: session?.storage,
          );
  }

  @override
  void dispose() {
    _controller?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final ProfilerViewController? controller = _controller;
    if (controller == null) {
      return ServerScope.of(context)?.state == ConnState.connecting
          ? LoadingState(label: reactorText(ReactorText.commonLoading))
          : EmptyState(
              message: reactorText(ReactorText.profilerUnsupported),
              icon: ArcaneIcon.flame(),
            );
    }
    return ListenableBuilder(
      listenable: controller,
      builder: (BuildContext context) => _body(context, controller),
    );
  }

  Widget _body(BuildContext context, ProfilerViewController controller) {
    final ServerSession? session = ServerSessionScope.of(context);
    final Object? error = controller.error;
    if (controller.status == null && error is ReactNotFound) {
      return EmptyState(
        message: reactorText(ReactorText.profilerUnsupported),
        icon: ArcaneIcon.flame(),
      );
    }
    final ProfileData? data = controller.data;
    return PageStack(
      children: <Widget>[
        ProfilerControls(
          controller: controller,
          snapshots: session?.clientAs<ISnapshotClient>(),
          snapshotLabel: component.snapshotLabel,
        ),
        if (data != null)
          ProfileView(
            data: data,
            serverId: session?.serverId ?? '',
            modded: session?.identity.modded ?? false,
            owner: component.owner,
            idle: controller.idle,
            onIdle: controller.setIdle,
          )
        else if (error != null)
          ErrorState(
            title: reactorText(ReactorText.profilerLoadFailed),
            message: localizedReactorError(error),
            onRetry: () => controller.load(force: true),
          )
        else
          LoadingState(label: reactorText(ReactorText.commonLoading)),
      ],
    );
  }
}
