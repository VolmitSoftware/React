library;

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;

import '../model/control_item.dart';
import '../localization/reactor_localizations.dart';
import '../model/knob.dart';
import '../model/role_info.dart';
import '../service/react_client.dart';
import '../state/control_list_controller.dart';
import '../state/control_scope.dart';
import '../state/role_scope.dart';
import '../ui/reactor_ui.dart';
import '../widget/knob_editor.dart';
import '../widget/role_badge.dart';
import '../widget/section_card.dart';

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
    return Collection(
      gap: 12,
      children: <Widget>[
        for (final ControlItem tweak in items)
          Card.flat(
            fillWidth: true,
            padding: EdgeInsets.zero,
            borderRadius: BorderRadius.zero,
            child: dom.div(
              styles: const dom.Styles(
                raw: <String, String>{
                  'padding': '0.75rem',
                  'display': 'flex',
                  'flex-direction': 'column',
                  'gap': '0.5rem',
                },
              ),
              <Widget>[
                Text.heading3(tweak.name),
                Text(
                  tweak.description,
                  color: TextColor.muted,
                  size: FontSize.sm,
                ),
                ArcaneToggleSwitch(
                  value: tweak.enabled,
                  disabled: readOnly,
                  onChanged: readOnly
                      ? null
                      : (bool b) => onToggle?.call(tweak.id, b),
                ),
                if (tweak.knobs.isNotEmpty)
                  ArcaneAccordion(
                    items: <ArcaneAccordionItem>[
                      ArcaneAccordionItem(
                        title: reactorText(
                          ReactorText.tweaksConfigure,
                          <String, Object?>{'count': tweak.knobs.length},
                        ),
                        customContent: Collection(
                          gap: 8,
                          children: <Widget>[
                            for (final Knob k in tweak.knobs)
                              KnobEditor(
                                knob: k,
                                disabled: readOnly,
                                onChanged: readOnly
                                    ? null
                                    : (Object? v) => onKnobChanged?.call(
                                        tweak.id,
                                        k.key,
                                        v,
                                      ),
                              ),
                          ],
                        ),
                      ),
                    ],
                  ),
              ],
            ),
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
    if (_client == null) {
      return ReactorPage(
        title: reactorText(ReactorText.tweaksTitle),
        subtitle: reactorText(ReactorText.tweaksSubtitle),
        children: <Widget>[
          sectionCard(
            label: reactorText(ReactorText.tweaksControl),
            child: dom.div(
              styles: const dom.Styles(
                raw: <String, String>{
                  'color': 'var(--muted-foreground)',
                  'font-size': '0.875rem',
                },
              ),
              <Widget>[
                Component.text(reactorText(ReactorText.tweaksLiveRequired)),
              ],
            ),
          ),
        ],
      );
    }

    final ControlListController controller = _controller!;

    if (controller.loading && controller.items.isEmpty) {
      return ReactorPage(
        title: reactorText(ReactorText.tweaksTitle),
        subtitle: reactorText(ReactorText.tweaksSubtitle),
        children: <Widget>[
          sectionCard(
            label: reactorText(ReactorText.tweaksControl),
            child: dom.div(
              styles: const dom.Styles(
                raw: <String, String>{
                  'color': 'var(--muted-foreground)',
                  'font-size': '0.875rem',
                },
              ),
              <Widget>[Component.text(reactorText(ReactorText.tweaksLoading))],
            ),
          ),
        ],
      );
    }

    final RoleInfo? role = RoleScope.of(context)?.role;
    final bool readOnly = readOnlyFor(role);

    return ReactorPage(
      title: reactorText(ReactorText.tweaksTitle),
      subtitle: reactorText(ReactorText.tweaksSubtitle),
      leading: RoleBadge(role: role),
      children: <Widget>[
        TweaksListView(
          items: controller.items,
          readOnly: readOnly,
          onToggle: readOnly ? null : controller.toggle,
          onKnobChanged: readOnly ? null : controller.setKnob,
        ),
      ],
    );
  }
}
