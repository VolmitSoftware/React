library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../model/control_item.dart';
import '../localization/reactor_localizations.dart';
import '../model/knob.dart';
import '../model/role_info.dart';
import '../service/react_client.dart';
import '../state/connection_manager.dart';
import '../state/control_list_controller.dart';
import '../state/control_scope.dart';
import '../state/role_scope.dart';
import '../state/server_scope.dart';
import '../ui/reactor_ui.dart';
import '../widget/knob_editor.dart';
import '../widget/role_badge.dart';

class TweaksListView extends StatelessWidget {
  final List<ControlItem> items;
  final void Function(String id, bool enabled)? onToggle;
  final void Function(String id, String key, Object? value)? onKnobChanged;
  final bool readOnly;

  const TweaksListView({
    required this.items,
    this.onToggle,
    this.onKnobChanged,
    this.readOnly = false,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    return SectionPanel(
      label: reactorText(ReactorText.tweaksControl),
      flush: true,
      child: items.isEmpty
          ? ReactorEmptyState(
              title: 'No tweaks available',
              description: 'React did not return any runtime tweaks.',
              icon: ArcaneIcon.slidersHorizontal(size: IconSize.sm),
            )
          : dom.div(
              styles: const dom.Styles(
                raw: <String, String>{
                  'display': 'flex',
                  'flex-direction': 'column',
                },
              ),
              <Widget>[for (final ControlItem tweak in items) _tweakRow(tweak)],
            ),
    );
  }

  Widget _tweakRow(ControlItem tweak) {
    return dom.div(
      styles: const dom.Styles(
        raw: <String, String>{
          'display': 'flex',
          'flex-direction': 'column',
          'gap': '0.5rem',
          'padding': '0.65rem 0.75rem',
          'border-bottom': '1px solid $kReactorHairline',
        },
      ),
      <Widget>[
        dom.div(
          styles: const dom.Styles(
            raw: <String, String>{
              'display': 'flex',
              'align-items': 'flex-start',
              'justify-content': 'space-between',
              'gap': '0.75rem',
            },
          ),
          <Widget>[
            dom.div(
              styles: const dom.Styles(
                raw: <String, String>{
                  'display': 'flex',
                  'min-width': '0',
                  'flex-direction': 'column',
                  'gap': '0.2rem',
                },
              ),
              <Widget>[
                reactorEyebrow(tweak.category),
                dom.strong(
                  styles: const dom.Styles(
                    raw: <String, String>{
                      'font-size': '0.82rem',
                      'line-height': '1.25',
                    },
                  ),
                  <Widget>[Component.text(tweak.name)],
                ),
                dom.span(
                  styles: const dom.Styles(
                    raw: <String, String>{
                      'color': kReactorMuted,
                      'font-size': '0.75rem',
                      'line-height': '1.4',
                    },
                  ),
                  <Widget>[Component.text(tweak.description)],
                ),
              ],
            ),
            dom.div(
              styles: const dom.Styles(
                raw: <String, String>{
                  'display': 'flex',
                  'align-items': 'center',
                  'gap': '0.5rem',
                },
              ),
              <Widget>[
                tweak.enabled
                    ? reactorBadge(
                        reactorText(ReactorText.commonEnabled),
                        ReactorStatus.healthy,
                      )
                    : reactorBadge(
                        reactorText(ReactorText.commonDisabled),
                        ReactorStatus.neutral,
                      ),
                ArcaneToggleSwitch(
                  value: tweak.enabled,
                  disabled: readOnly,
                  onChanged: readOnly
                      ? null
                      : (bool enabled) => onToggle?.call(tweak.id, enabled),
                ),
              ],
            ),
          ],
        ),
        if (tweak.knobs.isNotEmpty)
          dom.details(
            styles: const dom.Styles(
              raw: <String, String>{
                'margin': '0 -0.75rem -0.65rem',
                'border-top': '1px solid $kReactorHairline',
              },
            ),
            <Widget>[
              dom.summary(
                styles: const dom.Styles(
                  raw: <String, String>{
                    'min-height': '2rem',
                    'padding': '0.42rem 0.75rem',
                    'cursor': 'pointer',
                    'color': 'var(--foreground)',
                    'font-size': '0.72rem',
                    'font-weight': '600',
                    'line-height': '1.25',
                    'user-select': 'none',
                  },
                ),
                <Widget>[
                  Component.text(
                    reactorText(ReactorText.tweaksConfigure, <String, Object?>{
                      'count': tweak.knobs.length,
                    }),
                  ),
                ],
              ),
              dom.div(
                styles: const dom.Styles(
                  raw: <String, String>{
                    'display': 'grid',
                    'grid-template-columns':
                        'repeat(auto-fit, minmax(200px, 1fr))',
                    'gap': '0.65rem',
                    'padding': '0.65rem 0.75rem',
                    'border-top': '1px solid $kReactorHairline',
                    'background': 'var(--reactor-panel-soft)',
                  },
                ),
                <Widget>[
                  for (final Knob knob in tweak.knobs)
                    KnobEditor(
                      knob: knob,
                      disabled: readOnly,
                      onChanged: readOnly
                          ? null
                          : (Object? value) =>
                                onKnobChanged?.call(tweak.id, knob.key, value),
                    ),
                ],
              ),
            ],
          ),
      ],
    );
  }
}

class TweaksScreen extends StatefulWidget {
  const TweaksScreen({super.key});

  @override
  State<TweaksScreen> createState() => _TweaksScreenState();
}

class _TweaksScreenState extends State<TweaksScreen> {
  IControlClient? _client;
  ControlListController? _controller;
  bool _started = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final IControlClient? c = ControlScope.of(context)?.client;
    if (c != null && !_started) {
      _started = true;
      _client = c;
      _controller = ControlListController(
        c,
        isTweaks: true,
        onChange: () => setState(() {}),
        onError: (Object e) => ArcaneSonner.error(
          reactorText(ReactorText.commonUpdateFailed),
          description: e.toString(),
        ),
      );
      _controller!.load();
    }
  }

  @override
  Widget build(BuildContext context) {
    final ServerScope? server = ServerScope.of(context);
    if (server?.state == ConnState.connecting && _client == null) {
      return _statePage(
        ReactorLoadingState(label: reactorText(ReactorText.tweaksLoading)),
      );
    }
    if (_client == null) {
      return _statePage(
        ReactorNotice(
          title: 'Tweaks unavailable',
          message: reactorText(ReactorText.tweaksLiveRequired),
          status: ReactorStatus.critical,
        ),
      );
    }

    final ControlListController controller = _controller!;

    if (controller.loading && controller.items.isEmpty) {
      return _statePage(
        ReactorLoadingState(label: reactorText(ReactorText.tweaksLoading)),
      );
    }

    final Object? error = controller.error;
    if (error != null && controller.items.isEmpty) {
      return _statePage(
        ReactorNotice(
          title: reactorText(ReactorText.commonUpdateFailed),
          message: error.toString(),
          status: ReactorStatus.critical,
          action: Button.secondary(
            label: 'Retry',
            size: ButtonSize.small,
            onPressed: controller.load,
          ),
        ),
      );
    }

    final RoleInfo? role = RoleScope.of(context)?.role;
    final bool readOnly = readOnlyFor(role) || server?.state != ConnState.live;

    return ReactorPage(
      title: reactorText(ReactorText.tweaksTitle),
      subtitle: reactorText(ReactorText.tweaksSubtitle),
      leading: RoleBadge(role: role),
      children: <Widget>[
        if (error != null)
          ReactorNotice(
            title: reactorText(ReactorText.commonUpdateFailed),
            message: error.toString(),
            status: ReactorStatus.warning,
            action: Button.secondary(
              label: 'Retry',
              size: ButtonSize.small,
              onPressed: controller.load,
            ),
          ),
        TweaksListView(
          items: controller.items,
          readOnly: readOnly,
          onToggle: readOnly
              ? null
              : (String id, bool enabled) {
                  if (ServerScope.of(context)?.state == ConnState.live) {
                    controller.toggle(id, enabled);
                  }
                },
          onKnobChanged: readOnly
              ? null
              : (String id, String key, Object? value) {
                  if (ServerScope.of(context)?.state == ConnState.live) {
                    controller.setKnob(id, key, value);
                  }
                },
        ),
      ],
    );
  }

  Widget _statePage(Widget state) {
    return ReactorPage(
      title: reactorText(ReactorText.tweaksTitle),
      subtitle: reactorText(ReactorText.tweaksSubtitle),
      children: <Widget>[state],
    );
  }
}
