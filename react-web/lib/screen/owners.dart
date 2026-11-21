/// Plugins > Installed, or Mods > Installed: every loaded plugin or mod with
/// its live cost and its share of the cost of all of them.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component, ListenableBuilder;

import '../chart/share_layout.dart';
import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/owner_info.dart';
import '../model/sampler_sample.dart';
import '../service/react_client.dart';
import '../state/connection_manager.dart';
import '../state/owners_controller.dart';
import '../state/server_scope.dart';
import '../state/server_session.dart';
import '../state/server_session_scope.dart';
import '../ui/error_text.dart';
import '../ui/kit/badge.dart';
import '../ui/kit/data_list.dart';
import '../ui/kit/page.dart';
import '../ui/kit/states.dart';
import 'owner_charts.dart';
import 'profile_view.dart';

/// "1.23 ms/s".
String ownerCostText(double msPerSecond) =>
    '${msPerSecond.toStringAsFixed(2)} ms/s';

/// "12.3%".
String ownerShareText(double share) => '${(share * 100).toStringAsFixed(1)}%';

class OwnersScreen extends StatelessWidget {
  const OwnersScreen({required this.modded, super.key});

  /// Mods instead of plugins.
  final bool modded;

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final ServerSession? session = ServerSessionScope.of(context);
    if (session == null || !session.supports<IOwnerClient>()) {
      return ServerScope.of(context)?.state == ConnState.connecting
          ? LoadingState(label: reactorText(ReactorText.commonLoading))
          : EmptyState(message: reactorText(ReactorText.ownersLoadFailed));
    }
    final OwnersController controller = session.owners;
    return ListenableBuilder(
      listenable: controller,
      builder: (BuildContext context) => _body(context, controller),
    );
  }

  Widget _body(BuildContext context, OwnersController controller) {
    final List<OwnerInfo>? owners = controller.owners;
    if (owners == null) {
      final Object? error = controller.error;
      return error == null
          ? LoadingState(label: reactorText(ReactorText.commonLoading))
          : EmptyState(
              message: reactorText(ReactorText.ownersLoadFailed),
              description: localizedReactorError(error),
            );
    }
    final Map<String, SamplerSample> samples =
        ServerScope.of(context)?.snapshot?.byId ??
        const <String, SamplerSample>{};
    final Map<String, String> names = <String, String>{
      for (final OwnerInfo owner in owners) owner.id: owner.name,
    };
    final String serverId = ServerSessionScope.of(context)?.serverId ?? '';
    final List<OwnerCost> costs = ownerCosts(owners, samples);
    return PageStack(
      children: <Widget>[
        OwnerCostCharts(
          fallback: <ShareItem>[
            for (final OwnerCost cost in costs)
              if (cost.msPerSecond case final double ms)
                ShareItem(cost.owner.id, cost.owner.name, ms),
          ],
        ),
        _list(costs, names, serverId),
      ],
    );
  }

  Widget _list(
    List<OwnerCost> costs,
    Map<String, String> names,
    String serverId,
  ) {
    return DataList<OwnerCost>(
      href: (OwnerCost cost) => ownerPath(serverId, cost.owner.id),
      label: reactorText(
        modded ? ReactorText.pageModsTitle : ReactorText.pagePluginsTitle,
      ),
      items: costs,
      emptyMessage: reactorText(
        modded ? ReactorText.ownersNoneMods : ReactorText.ownersNonePlugins,
      ),
      columns: <ListColumn<OwnerCost>>[
        ListColumn<OwnerCost>(
          reactorText(
            modded ? ReactorText.ownersMod : ReactorText.ownersPlugin,
          ),
          _name,
          width: 'minmax(0, 2fr)',
        ),
        ListColumn<OwnerCost>.text(
          reactorText(ReactorText.ownersVersion),
          (OwnerCost cost) => cost.owner.version,
          width: 'minmax(0, 1fr)',
        ),
        ListColumn<OwnerCost>(
          reactorText(ReactorText.ownersCost),
          (OwnerCost cost) => oneLineText(_cost(cost, names)),
          numeric: true,
          width: '8rem',
        ),
        ListColumn<OwnerCost>.text(
          reactorText(ReactorText.ownersShare),
          (OwnerCost cost) =>
              cost.msPerSecond == null ? '—' : ownerShareText(cost.share),
          numeric: true,
          width: '5rem',
        ),
        ListColumn<OwnerCost>(
          reactorText(ReactorText.commonEnabled),
          (OwnerCost cost) => StatusBadge(
            reactorText(
              cost.owner.enabled
                  ? ReactorText.commonEnabled
                  : ReactorText.commonDisabled,
            ),
            tone: cost.owner.enabled ? BadgeTone.ok : BadgeTone.neutral,
          ),
          width: '7rem',
        ),
      ],
    );
  }

  /// The name, then the id when it differs.
  Widget _name(OwnerCost cost) {
    final OwnerInfo owner = cost.owner;
    return dom.div(
      classes: 'reactor-row-title',
      attributes: <String, String>{'data-owner': owner.id},
      <Widget>[
        oneLineText(owner.name),
        if (owner.id != owner.name)
          dom.span(
            styles: const dom.Styles(
              raw: <String, String>{'color': 'var(--muted-foreground)'},
            ),
            <Widget>[Component.text(owner.id)],
          ),
      ],
    );
  }

  String _cost(OwnerCost cost, Map<String, String> names) {
    final String? parent = cost.owner.includedIn;
    if (parent != null) {
      return reactorText(ReactorText.ownersIncludedIn, <String, Object?>{
        'owner': names[parent] ?? parent,
      });
    }
    final double? msPerSecond = cost.msPerSecond;
    return msPerSecond == null ? '—' : ownerCostText(msPerSecond);
  }
}
