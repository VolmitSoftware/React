import 'dart:convert';

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;
import 'package:jaspr_router/jaspr_router.dart';

import '../model/server_credential.dart';
import '../localization/reactor_locale.dart';
import '../localization/reactor_localizations.dart';
import '../service/relay_identity.dart';
import '../state/fleet_manager.dart';
import '../state/fleet_scope.dart';
import '../ui/reactor_ui.dart';

class PairingCode {
  final String directUrl;
  final String relayUrl;
  final String serverPubKey;
  final String fingerprint;
  final String tokenId;
  final String tokenSig;

  const PairingCode({
    required this.directUrl,
    required this.relayUrl,
    required this.serverPubKey,
    required this.fingerprint,
    required this.tokenId,
    required this.tokenSig,
  });

  static const String _prefix = 'RCT2.';
  static final RegExp _codePattern = RegExp(r'RCT2\.[A-Za-z0-9_-]+={0,2}');
  static final RegExp _fingerprintPattern = RegExp(r'^[a-f0-9]{64}$');

  static String encodeRaw(String jsonString) =>
      base64Url.encode(utf8.encode(jsonString)).replaceAll('=', '');

  static String normalizeInput(String input) {
    final String trimmed = input.trim();
    final RegExpMatch? match = _codePattern.firstMatch(trimmed);
    if (match != null) return match.group(0)!.replaceAll(RegExp(r'\s+'), '');
    return trimmed.replaceAll(RegExp(r'\s+'), '');
  }

  static String encode({
    required String directUrl,
    required String relayUrl,
    required String serverPubKey,
    required String fingerprint,
    required String tokenId,
    required String tokenSig,
  }) {
    final Map<String, dynamic> payload = <String, dynamic>{
      'directUrl': directUrl,
      'relayUrl': relayUrl,
      'serverPubKey': serverPubKey,
      'fingerprint': fingerprint,
      'tokenId': tokenId,
      'tokenSig': tokenSig,
    };
    final String json = jsonEncode(payload);
    return '$_prefix${encodeRaw(json)}';
  }

  static PairingCode? decode(String code) {
    final String normalized = normalizeInput(code);
    if (!normalized.startsWith(_prefix)) return null;
    final String b64 = normalized.substring(_prefix.length);
    try {
      final String json = utf8.decode(
        base64Url.decode(base64Url.normalize(b64)),
      );
      final Map<String, dynamic> map = jsonDecode(json) as Map<String, dynamic>;

      final Object? directUrlRaw = map['directUrl'];
      final Object? relayUrlRaw = map['relayUrl'];
      final Object? serverPubKeyRaw = map['serverPubKey'];
      final Object? fingerprintRaw = map['fingerprint'];
      final Object? tokenId = map['tokenId'];
      final Object? tokenSig = map['tokenSig'];

      if (directUrlRaw is! String ||
          relayUrlRaw is! String ||
          serverPubKeyRaw is! String ||
          fingerprintRaw is! String ||
          tokenId is! String ||
          tokenSig is! String) {
        return null;
      }

      if (!_validDirectUrl(directUrlRaw) || !_validRelayUrl(relayUrlRaw)) {
        return null;
      }
      if (directUrlRaw.isEmpty && relayUrlRaw.isEmpty) return null;
      if (serverPubKeyRaw.isEmpty ||
          !_fingerprintPattern.hasMatch(fingerprintRaw)) {
        return null;
      }
      if (!RelayIdentity.codeConsistent(
        serverPubKey: serverPubKeyRaw,
        fingerprint: fingerprintRaw,
      )) {
        return null;
      }

      return PairingCode(
        directUrl: directUrlRaw,
        relayUrl: relayUrlRaw,
        serverPubKey: serverPubKeyRaw,
        fingerprint: fingerprintRaw,
        tokenId: tokenId,
        tokenSig: tokenSig,
      );
    } on Object {
      return null;
    }
  }

  static ReactorText? validationMessage(String code) {
    final String normalized = normalizeInput(code);
    if (normalized.isEmpty) {
      return ReactorText.addServerPasteFullCode;
    }
    if (!normalized.startsWith(_prefix)) {
      return ReactorText.addServerPrefixRequired;
    }
    final String b64 = normalized.substring(_prefix.length);
    if (b64.isEmpty) return ReactorText.addServerPayloadMissing;
    if (b64.length % 4 == 1) {
      return ReactorText.addServerCodeIncomplete;
    }
    if (decode(normalized) == null) {
      return ReactorText.addServerDecodeFailed;
    }
    return null;
  }

  ServerCredential toCredential({required String id, required String label}) {
    final Uri? direct = directUrl.isEmpty ? null : Uri.tryParse(directUrl);
    return ServerCredential(
      id: id,
      label: label,
      host: direct?.host ?? '',
      port: direct?.hasPort == true
          ? direct!.port
          : direct?.scheme == 'https'
          ? 443
          : direct == null
          ? 0
          : 80,
      bearer: '$tokenId.$tokenSig',
      secure: direct?.scheme == 'https',
      relayUrl: relayUrl.isEmpty ? null : relayUrl,
      serverPubKey: serverPubKey,
      fingerprint: fingerprint,
    );
  }

  static bool _validDirectUrl(String value) {
    if (value.isEmpty) return true;
    final Uri? uri = Uri.tryParse(value);
    if (uri == null || !uri.hasAuthority || uri.host.isEmpty) return false;
    if (uri.scheme != 'http' && uri.scheme != 'https') return false;
    return uri.userInfo.isEmpty &&
        uri.query.isEmpty &&
        uri.fragment.isEmpty &&
        (uri.path.isEmpty || uri.path == '/');
  }

  static bool _validRelayUrl(String value) {
    if (value.isEmpty) return true;
    final Uri? uri = Uri.tryParse(value);
    if (uri == null || !uri.hasAuthority || uri.host.isEmpty) return false;
    if (uri.scheme != 'ws' && uri.scheme != 'wss') return false;
    return uri.userInfo.isEmpty && uri.query.isEmpty && uri.fragment.isEmpty;
  }
}

class AddServerScreen extends StatefulWidget {
  final FleetManager fleetManager;

  const AddServerScreen({required this.fleetManager, super.key});

  static Future<ServerCredential> pairServer(
    String code,
    FleetManager fm,
  ) async {
    final PairingCode? pc = PairingCode.decode(code);
    if (pc == null) {
      throw ArgumentError.value(code, 'code', 'Invalid RCT2 pairing code');
    }
    final String id = DateTime.now().microsecondsSinceEpoch.toRadixString(36);
    final Uri? direct = pc.directUrl.isEmpty
        ? null
        : Uri.tryParse(pc.directUrl);
    final String label = direct?.host.isNotEmpty == true
        ? direct!.host
        : 'React ${pc.fingerprint.substring(0, 12)}';
    final ServerCredential cred = pc.toCredential(id: id, label: label);
    await fm.add(cred);
    return cred;
  }

  @override
  State<AddServerScreen> createState() => _AddServerScreenState();
}

class _AddServerScreenState extends State<AddServerScreen> {
  String _code = '';
  PairingCode? _decoded;
  ReactorText? _validationMessage;
  ReactorText? _pairingMessage;
  bool _confirmReset = false;
  bool _loading = false;
  bool _pairingFailed = false;

  void _onCodeChanged(String value) {
    final ReactorText? validation = value.trim().isEmpty
        ? null
        : PairingCode.validationMessage(value);
    setState(() {
      _code = value;
      _decoded = PairingCode.decode(value);
      _validationMessage = validation;
      _pairingMessage = null;
      _confirmReset = false;
      _pairingFailed = false;
    });
  }

  void _clearCode() {
    setState(() {
      _code = '';
      _decoded = null;
      _validationMessage = null;
      _pairingMessage = null;
      _confirmReset = false;
      _pairingFailed = false;
    });
  }

  void _resetFleet() {
    final FleetController? fleet = FleetScope.of(context);
    if (fleet == null) return;
    if (!_confirmReset && fleet.fleetManager.servers.isNotEmpty) {
      setState(() => _confirmReset = true);
      return;
    }
    fleet.clearFleet();
    setState(() {
      _confirmReset = false;
      _pairingMessage = ReactorText.addServerFleetClearedMessage;
      _pairingFailed = false;
    });
    ArcaneSonner.success(reactorText(ReactorText.addServerFleetReset));
  }

  Future<void> _onPair([String? submittedCode]) async {
    final String rawCode = submittedCode ?? _code;
    final ReactorText? validation = PairingCode.validationMessage(rawCode);
    if (validation != null) {
      setState(() {
        _code = rawCode;
        _decoded = PairingCode.decode(rawCode);
        _validationMessage = validation;
        _pairingMessage = null;
        _pairingFailed = false;
      });
      return;
    }
    setState(() {
      _code = rawCode;
      _decoded = PairingCode.decode(rawCode);
      _validationMessage = null;
      _pairingMessage = ReactorText.addServerConnectingIdentity;
      _loading = true;
      _pairingFailed = false;
    });
    try {
      final ServerCredential cred = await AddServerScreen.pairServer(
        rawCode,
        component.fleetManager,
      );
      component.fleetManager.setActive(cred.id);
      FleetScope.of(context)?.trackPaired(cred.id);
      ArcaneSonner.success(reactorText(ReactorText.addServerPaired));
      context.push('/server/${cred.id}/overview');
    } on ArgumentError {
      if (!mounted) return;
      setState(() {
        _pairingMessage = ReactorText.addServerInvalidPairingMessage;
        _validationMessage = _pairingMessage;
        _pairingFailed = true;
      });
      ArcaneSonner.error(
        reactorText(ReactorText.addServerInvalidPairingCode),
        description: reactorText(
          ReactorText.addServerInvalidPairingDescription,
        ),
      );
    } on Object catch (e) {
      if (!mounted) return;
      setState(() {
        _pairingMessage = ReactorText.addServerConnectionFailedMessage;
        _pairingFailed = true;
      });
      ArcaneSonner.error(
        reactorText(ReactorText.addServerPairingFailed),
        description: localizedReactorError(e),
      );
    } finally {
      if (mounted) {
        setState(() {
          _loading = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    dependOnReactorLocale(context);
    final PairingCode? decoded = _decoded;
    final FleetController? fleet = FleetScope.of(context);
    final int savedCount = fleet?.fleetManager.servers.length ?? 0;
    final bool canPair = !_loading && decoded != null;
    final String? inputError = _validationMessage == null
        ? null
        : reactorText(_validationMessage!);
    return ReactorPage(
      title: reactorText(ReactorText.addServerTitle),
      subtitle: reactorText(ReactorText.addServerSubtitle),
      actions: dom.div(classes: 'reactor-add-actions', <Widget>[
        Button.secondary(
          label: reactorText(ReactorText.addServerClearCode),
          size: ButtonSize.small,
          disabled: _loading || _code.isEmpty,
          onPressed: _loading || _code.isEmpty ? null : _clearCode,
        ),
        Button.destructive(
          label: _confirmReset
              ? reactorText(ReactorText.addServerConfirmReset)
              : reactorText(ReactorText.addServerResetFleet),
          size: ButtonSize.small,
          disabled: _loading || savedCount == 0,
          onPressed: _loading || savedCount == 0 ? null : _resetFleet,
        ),
        Button(
          label: _loading
              ? reactorText(ReactorText.addServerConnecting)
              : reactorText(ReactorText.addServerPair),
          size: ButtonSize.small,
          disabled: !canPair,
          onPressed: canPair ? () => _onPair() : null,
        ),
      ]),
      children: <Widget>[
        SectionPanel(
          label: reactorText(ReactorText.addServerConnectionFlow),
          description: reactorText(
            ReactorText.addServerConnectionFlowDescription,
          ),
          flush: true,
          child: _pairingGuide(),
        ),
        SectionPanel(
          label: reactorText(ReactorText.addServerPairingConsole),
          description: reactorText(
            ReactorText.addServerPairingConsoleDescription,
          ),
          children: <Widget>[
            dom.div(
              styles: const dom.Styles(
                raw: <String, String>{
                  'display': 'flex',
                  'flex-direction': 'column',
                  'gap': '0.75rem',
                },
              ),
              <Widget>[
                TextInput(
                  placeholder: reactorText(
                    ReactorText.addServerInputPlaceholder,
                  ),
                  attributes: const <String, String>{'dir': 'ltr'},
                  value: _code,
                  onChange: _onCodeChanged,
                  onSubmit: _onPair,
                  error: inputError,
                  helperText: reactorText(ReactorText.addServerInputHelper),
                  fullWidth: true,
                ),
                if (inputError != null)
                  ReactorNotice(
                    title: reactorText(ReactorText.addServerCheckCode),
                    message: inputError,
                    status: ReactorStatus.warning,
                  )
                else if (_pairingFailed && _pairingMessage != null)
                  ReactorNotice(
                    title: reactorText(ReactorText.addServerPairingFailed),
                    message: reactorText(_pairingMessage!),
                    status: ReactorStatus.critical,
                  )
                else if (_loading)
                  ReactorLoadingState(
                    label: reactorText(ReactorText.addServerConnectingIdentity),
                  )
                else if (_pairingMessage != null)
                  ReactorNotice(
                    title: reactorText(ReactorText.addServerFleetReset),
                    message: reactorText(_pairingMessage!),
                    status: ReactorStatus.info,
                  )
                else if (decoded == null)
                  ReactorEmptyState(
                    title: reactorText(ReactorText.addServerAwaitingCode),
                    description: reactorText(ReactorText.addServerInputHelper),
                    icon: ArcaneIcon.serverCog(size: IconSize.sm),
                  )
                else
                  _connectionDetails(decoded),
              ],
            ),
          ],
        ),
      ],
    );
  }

  Widget _pairingGuide() {
    return dom.div(
      classes: 'reactor-add-step-list',
      attributes: const <String, String>{'role': 'list'},
      <Widget>[
        _pairingStep(
          number: '01',
          title: reactorText(ReactorText.addServerCopy),
          description: reactorText(ReactorText.addServerCopyDescription),
          command: 'plugins/React/web.toml: enabled = true',
        ),
        _pairingStep(
          number: '02',
          title: reactorText(ReactorText.addServerDecode),
          description: reactorText(ReactorText.addServerDecodeDescription),
          command: '/react web pair my-server viewer',
        ),
        _pairingStep(
          number: '03',
          title: reactorText(ReactorText.addServerMonitor),
          description: reactorText(ReactorText.addServerMonitorDescription),
          command: 'RCT2.\u2026',
        ),
        _pairingStep(
          number: '04',
          title: reactorText(ReactorText.addServerSecurity),
          description: reactorText(ReactorText.addServerSecurityDescription),
          command: '/react web revoke <token-id>',
        ),
      ],
    );
  }

  Widget _pairingStep({
    required String number,
    required String title,
    required String description,
    required String command,
  }) {
    return dom.div(
      classes: 'reactor-add-step',
      attributes: const <String, String>{'role': 'listitem'},
      <Widget>[
        dom.span(classes: 'reactor-add-step-label', <Widget>[
          Component.text(number),
        ]),
        dom.div(classes: 'reactor-add-step-copy', <Widget>[
          dom.strong(<Widget>[Component.text(title)]),
          dom.p(<Widget>[Component.text(description)]),
          dom.code(classes: 'reactor-add-step-command', <Widget>[
            Component.text(command),
          ]),
        ]),
      ],
    );
  }

  Widget _connectionDetails(PairingCode decoded) {
    return dom.div(
      styles: const dom.Styles(
        raw: <String, String>{
          'border-top': '1px solid var(--border)',
          'border-bottom': '1px solid var(--border)',
        },
      ),
      <Widget>[
        _detailRow(
          reactorText(ReactorText.addServerHost),
          decoded.directUrl.isEmpty
              ? reactorText(ReactorText.addServerRelayOnly)
              : Uri.parse(decoded.directUrl).host,
        ),
        _detailRow(
          reactorText(ReactorText.addServerPort),
          decoded.directUrl.isEmpty
              ? reactorText(ReactorText.addServerRelay)
              : Uri.parse(decoded.directUrl).hasPort
              ? Uri.parse(decoded.directUrl).port.toString()
              : Uri.parse(decoded.directUrl).scheme == 'https'
              ? '443'
              : '80',
        ),
        _detailRow(
          reactorText(ReactorText.addServerFingerprint),
          decoded.fingerprint.substring(0, 16),
        ),
        _detailRow(
          reactorText(ReactorText.addServerTransport),
          decoded.relayUrl.isEmpty
              ? reactorText(ReactorText.addServerDirectHost)
              : reactorText(ReactorText.addServerRelayChannel),
        ),
      ],
    );
  }

  Widget _detailRow(String label, String value) {
    return dom.div(
      styles: const dom.Styles(
        raw: <String, String>{
          'display': 'grid',
          'grid-template-columns': 'minmax(120px, 0.35fr) minmax(0, 1fr)',
          'align-items': 'center',
          'gap': '1rem',
          'padding': '0.65rem 0',
          'border-bottom': '1px solid var(--border)',
        },
      ),
      <Widget>[
        dom.span(
          styles: const dom.Styles(
            raw: <String, String>{
              'color': 'var(--muted-foreground)',
              'font-size': '0.75rem',
              'font-weight': '600',
              'text-transform': 'uppercase',
              'letter-spacing': '0.06em',
            },
          ),
          <Widget>[Component.text(label)],
        ),
        dom.span(
          styles: const dom.Styles(
            raw: <String, String>{
              'min-width': '0',
              'overflow': 'hidden',
              'text-overflow': 'ellipsis',
              'font-family': 'monospace',
              'font-size': '0.8125rem',
            },
          ),
          <Widget>[Component.text(value)],
        ),
      ],
    );
  }
}
