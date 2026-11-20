/// One profile, live or frozen in a snapshot: the owner ranking, the hot
/// methods, the call tree and the threads. Narrowed to one owner it drops
/// the ranking and the owner column and colours that owner's frames.
library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../app/paths.dart';
import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../model/profiler.dart';
import '../model/units.dart';
import '../ui/kit/card.dart';
import '../ui/kit/data_list.dart';
import '../ui/kit/page.dart';
import '../ui/kit/states.dart';
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

class ProfileView extends StatelessWidget {
  const ProfileView({
    required this.data,
    required this.serverId,
    required this.modded,
    this.owner,
    super.key,
  });

  final ProfileData data;
  final String serverId;
  final bool modded;

  /// The owner the view narrows to.
  final String? owner;

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    if (data.status.samples == 0 && data.tree.roots.isEmpty) {
      return EmptyState(
        message: reactorText(ReactorText.profilerNoSamples),
        icon: ArcaneIcon.flame(),
      );
    }
    final String? only = owner;
    return PageStack(
      children: <Widget>[
        if (only == null) _owners(),
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

  Widget _tree(String? only) => AppCard(
    title: reactorText(ReactorText.profilerCallTree),
    attributes: const <String, String>{'data-profiler': 'tree'},
    children: <Widget>[
      if (data.tree.roots.isEmpty)
        dom.p(classes: 'ui-caption', <Widget>[
          Component.text(reactorText(ReactorText.profilerNoSamples)),
        ])
      else
        FlameGraph(
          tree: data.tree,
          rootLabel: reactorText(ReactorText.profilerThreadsAll),
          highlight: only,
        ),
    ],
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
