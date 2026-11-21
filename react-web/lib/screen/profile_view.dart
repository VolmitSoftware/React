/// One profile, live or frozen in a snapshot: the owner share and ranking,
/// the hot methods, the call tree (a list or a flame graph, filtered by
/// method) and the threads. Narrowed to one owner it shows that owner's
/// share instead of the ranking, drops the owner column and marks that
/// owner's frames.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../app/paths.dart';
import '../chart/share_charts.dart';
import '../chart/share_layout.dart';
import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/profiler.dart';
import '../model/units.dart';
import '../ui/kit/action_row.dart';
import '../ui/kit/card.dart';
import '../ui/kit/data_list.dart';
import '../ui/kit/density.dart';
import '../ui/kit/page.dart';
import '../ui/kit/states.dart';
import '../ui/kit/toggle_choice.dart';
import '../widget/call_tree_view.dart';
import '../widget/flame_graph.dart';

/// The address of [owner]'s page on server [serverId].
String ownerPath(String serverId, String owner) =>
    serverPath(serverId, 'plugins/${Uri.encodeComponent(owner)}');

/// A method name on one line: the class and method, the whole frame as its
/// title.
Widget methodText(String method) => dom.span(
  classes: 'reactor-one-line',
  attributes: <String, String>{'title': method},
  <Widget>[Component.text(profilerShortMethod(method))],
);

/// How the call tree is drawn.
enum CallTreeMode { tree, flame }

class ProfileView extends StatefulWidget {
  const ProfileView({
    required this.data,
    required this.serverId,
    required this.modded,
    this.owner,
    this.idle = false,
    this.onIdle,
    super.key,
  });

  final ProfileData data;
  final String serverId;
  final bool modded;

  /// The owner the view narrows to.
  final String? owner;

  /// Whether idle samples are kept, and the switch for it; no switch when
  /// [onIdle] is null (a snapshot).
  final bool idle;
  final void Function(bool idle)? onIdle;

  @override
  State<ProfileView> createState() => _ProfileViewState();
}

class _ProfileViewState extends State<ProfileView> {
  CallTreeMode _mode = CallTreeMode.tree;
  String _query = '';

  ProfileData get data => component.data;
  String get serverId => component.serverId;
  bool get modded => component.modded;

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    if (data.status.samples == 0 && data.tree.roots.isEmpty) {
      return EmptyState(
        message: reactorText(ReactorText.profilerNoSamples),
        icon: ArcaneIcon.flame(),
      );
    }
    final String? only = component.owner;
    return PageStack(
      children: <Widget>[
        if (only == null) ...<Widget>[_ownerShare(), _owners()],
        if (only != null) _share(only),
        _hot(only),
        _tree(only),
        _threads(),
      ],
    );
  }

  Widget _owners() => DataList<ProfilerOwner>(
    title: reactorText(
      modded ? ReactorText.pageModsTitle : ReactorText.pagePluginsTitle,
    ),
    label: reactorText(
      modded ? ReactorText.pageModsTitle : ReactorText.pagePluginsTitle,
    ),
    items: data.owners.owners,
    emptyMessage: reactorText(ReactorText.profilerNoSamples),
    href: (ProfilerOwner item) => ownerPath(serverId, item.id),
    columns: <ListColumn<ProfilerOwner>>[
      ListColumn<ProfilerOwner>.text(
        reactorText(modded ? ReactorText.ownersMod : ReactorText.ownersPlugin),
        (ProfilerOwner item) => item.name,
        width: 'minmax(0, 1.4fr)',
      ),
      ListColumn<ProfilerOwner>.text(
        reactorText(ReactorText.ownersShare),
        (ProfilerOwner item) => profilerPercentText(item.percent),
        numeric: true,
        width: '5rem',
      ),
      ListColumn<ProfilerOwner>.text(
        reactorText(ReactorText.profilerSamples),
        (ProfilerOwner item) => groupThousands(item.samples),
        numeric: true,
        width: '6rem',
      ),
      ListColumn<ProfilerOwner>(
        reactorText(ReactorText.profilerTopMethod),
        (ProfilerOwner item) => item.top.isEmpty
            ? oneLineText('')
            : methodText(item.top.first.method),
        width: 'minmax(0, 2fr)',
      ),
    ],
  );

  Widget _hot(String? only) {
    final ProfilerHot hot = data.hot.ownedBy(only);
    return DataList<ProfilerHotMethod>(
      title: reactorText(ReactorText.profilerHotMethods),
      label: reactorText(ReactorText.profilerHotMethods),
      items: hot.methods,
      emptyMessage: reactorText(ReactorText.profilerNoSamples),
      columns: <ListColumn<ProfilerHotMethod>>[
        ListColumn<ProfilerHotMethod>(
          reactorText(ReactorText.profilerMethod),
          (ProfilerHotMethod item) => methodText(item.method),
          width: 'minmax(0, 3fr)',
        ),
        if (only == null)
          ListColumn<ProfilerHotMethod>.text(
            reactorText(
              modded ? ReactorText.ownersMod : ReactorText.ownersPlugin,
            ),
            (ProfilerHotMethod item) => item.ownerName,
            width: 'minmax(0, 1fr)',
          ),
        ListColumn<ProfilerHotMethod>.text(
          reactorText(ReactorText.profilerSelf),
          (ProfilerHotMethod item) => profilerPercentText(item.selfPercent),
          numeric: true,
          width: '5rem',
        ),
        ListColumn<ProfilerHotMethod>.text(
          reactorText(ReactorText.profilerTotal),
          (ProfilerHotMethod item) => profilerPercentText(item.totalPercent),
          numeric: true,
          width: '5rem',
        ),
      ],
    );
  }

  Widget _ownerShare() {
    final List<ShareSlice> slices = shareSlices(<ShareItem>[
      for (final ProfilerOwner owner in data.owners.owners)
        ShareItem(owner.id, owner.name, owner.samples.toDouble()),
    ], otherLabel: reactorText(ReactorText.chartOther));
    return AppCard(
      title: reactorText(ReactorText.profilerShare),
      attributes: const <String, String>{'data-profiler': 'share'},
      children: <Widget>[
        if (slices.isEmpty)
          dom.p(classes: 'ui-caption', <Widget>[
            Component.text(reactorText(ReactorText.profilerNoSamples)),
          ])
        else
          DonutChart(
            slices: slices,
            label: reactorText(ReactorText.profilerShare),
            total: groupThousands(data.owners.samples),
            valueText: (double samples) => groupThousands(samples.round()),
            href: (String id) => ownerPath(serverId, id),
          ),
      ],
    );
  }

  /// [only]'s part of every sample beside the rest.
  Widget _share(String only) {
    final ProfilerOwner? owner = data.owners.byId(only);
    final int whole = data.owners.samples;
    final int mine = owner?.samples ?? 0;
    return AppCard(
      title: reactorText(ReactorText.profilerShare),
      attributes: const <String, String>{'data-profiler': 'share'},
      children: <Widget>[
        if (whole <= 0)
          dom.p(classes: 'ui-caption', <Widget>[
            Component.text(reactorText(ReactorText.profilerNoSamples)),
          ])
        else
          DonutChart(
            slices: orderedSlices(<ShareItem>[
              ShareItem(only, owner?.name ?? only, mine.toDouble()),
              ShareItem(
                '',
                reactorText(ReactorText.chartOther),
                (whole - mine).toDouble(),
              ),
            ]),
            label: reactorText(ReactorText.profilerShare),
            total: profilerPercentText(whole == 0 ? 0 : mine * 100 / whole),
            valueText: (double samples) => groupThousands(samples.round()),
            colorOf: (ShareSlice slice) =>
                kSeriesColors[slice.id == only ? 1 : kSeriesColors.length - 1],
          ),
      ],
    );
  }

  ActionRow _treeTools() {
    final void Function(bool idle)? onIdle = component.onIdle;
    return ActionRow(
      dense: true,
      label: reactorText(ReactorText.profilerCallTree),
      leading: <Widget>[
        dom.div(classes: 'reactor-grow', <Widget>[
          TextInput(
            type: TextInputType.search,
            size: controlSize(Density.dense),
            value: _query,
            placeholder: reactorText(ReactorText.profilerFilter),
            attributes: <String, String>{
              'aria-label': reactorText(ReactorText.profilerFilter),
              'data-profiler': 'filter',
            },
            fullWidth: true,
            onChanged: (String value) => setState(() => _query = value),
          ),
        ]),
        ToggleChoice<CallTreeMode>(
          label: reactorText(ReactorText.profilerView),
          value: _mode,
          options: <ChoiceOption<CallTreeMode>>[
            ChoiceOption<CallTreeMode>(
              CallTreeMode.tree,
              reactorText(ReactorText.profilerViewTree),
            ),
            ChoiceOption<CallTreeMode>(
              CallTreeMode.flame,
              reactorText(ReactorText.profilerViewFlame),
            ),
          ],
          onChanged: (CallTreeMode mode) => setState(() => _mode = mode),
        ),
        if (onIdle != null)
          dom.div(
            styles: const dom.Styles(
              raw: <String, String>{
                'display': 'inline-flex',
                'align-items': 'center',
                'min-height': '2rem',
                'white-space': 'nowrap',
              },
            ),
            attributes: const <String, String>{'data-profiler': 'idle'},
            <Widget>[
              ArcaneToggleSwitch(
                value: component.idle,
                label: reactorText(ReactorText.profilerIdle),
                size: controlSize(Density.dense),
                onChanged: onIdle,
              ),
            ],
          ),
      ],
    );
  }

  Widget _tree(String? only) {
    final ProfilerTree tree = data.tree;
    return AppCard(
      title: reactorText(ReactorText.profilerCallTree),
      attributes: const <String, String>{'data-profiler': 'tree'},
      actions: _treeTools(),
      children: <Widget>[
        if (tree.roots.isEmpty)
          dom.p(classes: 'ui-caption', <Widget>[
            Component.text(reactorText(ReactorText.profilerNoSamples)),
          ])
        else if (_mode == CallTreeMode.flame)
          FlameGraph(
            tree: tree,
            rootLabel: reactorText(ReactorText.profilerThreadsAll),
            highlight: only,
          )
        else
          _list(tree, only),
      ],
    );
  }

  Widget _list(ProfilerTree tree, String? only) => CallTreeView(
    tree: tree,
    label: reactorText(ReactorText.profilerCallTree),
    totalLabel: reactorText(ReactorText.profilerTotal),
    selfLabel: reactorText(ReactorText.profilerSelf),
    noMatches: reactorText(ReactorText.profilerNoMatches),
    query: _query,
    highlight: only,
  );

  Widget _threads() {
    final List<ProfilerThread> threads = data.threads;
    final bool states = threads.any(
      (ProfilerThread thread) => thread.state.isNotEmpty,
    );
    return DataList<ProfilerThread>(
      title: reactorText(ReactorText.profilerThreads),
      label: reactorText(ReactorText.profilerThreads),
      items: threads,
      emptyMessage: reactorText(ReactorText.profilerNoSamples),
      columns: <ListColumn<ProfilerThread>>[
        ListColumn<ProfilerThread>.text(
          reactorText(ReactorText.profilerThread),
          (ProfilerThread item) => item.name,
          width: 'minmax(0, 2fr)',
        ),
        if (states)
          ListColumn<ProfilerThread>.text(
            reactorText(ReactorText.profilerThreadState),
            (ProfilerThread item) => item.state,
            width: 'minmax(0, 1fr)',
          ),
        ListColumn<ProfilerThread>.text(
          reactorText(ReactorText.ownersShare),
          (ProfilerThread item) => profilerPercentText(item.percent),
          numeric: true,
          width: '5rem',
        ),
        ListColumn<ProfilerThread>.text(
          reactorText(ReactorText.profilerSamples),
          (ProfilerThread item) => groupThousands(item.samples),
          numeric: true,
          width: '6rem',
        ),
      ],
    );
  }
}
