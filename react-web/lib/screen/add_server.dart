import 'dart:convert';

import 'package:arcane_jaspr/arcane_jaspr.dart';
import 'package:jaspr/dom.dart' as dom;
import 'package:jaspr/jaspr.dart' show Component;
import 'package:jaspr_router/jaspr_router.dart';

import '../model/server_credential.dart';
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

  static String? validationMessage(String code) {
    final String normalized = normalizeInput(code);
    if (normalized.isEmpty) {
      return reactorText(ReactorText.addServerPasteFullCode);
    }
    if (!normalized.startsWith(_prefix)) {
      return reactorText(ReactorText.addServerPrefixRequired);
    }
    final String b64 = normalized.substring(_prefix.length);
    if (b64.isEmpty) return reactorText(ReactorText.addServerPayloadMissing);
    if (b64.length % 4 == 1) {
      return reactorText(ReactorText.addServerCodeIncomplete);
    }
    if (decode(normalized) == null) {
      return reactorText(ReactorText.addServerDecodeFailed);
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
  String? _validationMessage;
  String? _pairingMessage;
  bool _confirmReset = false;
  bool _loading = false;

  void _onCodeChanged(String value) {
    final String? validation = value.trim().isEmpty
        ? null
        : PairingCode.validationMessage(value);
    setState(() {
      _code = value;
      _decoded = PairingCode.decode(value);
      _validationMessage = validation;
      _pairingMessage = null;
      _confirmReset = false;
    });
  }

  void _clearCode() {
    setState(() {
      _code = '';
      _decoded = null;
      _validationMessage = null;
      _pairingMessage = null;
      _confirmReset = false;
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
      _pairingMessage = reactorText(ReactorText.addServerFleetClearedMessage);
    });
    ArcaneSonner.success(reactorText(ReactorText.addServerFleetReset));
  }

  Future<void> _onPair([String? submittedCode]) async {
    final String rawCode = submittedCode ?? _code;
    final String? validation = PairingCode.validationMessage(rawCode);
    if (validation != null) {
      setState(() {
        _code = rawCode;
        _decoded = PairingCode.decode(rawCode);
        _validationMessage = validation;
        _pairingMessage = validation;
      });
      return;
    }
    setState(() {
      _code = rawCode;
      _decoded = PairingCode.decode(rawCode);
      _validationMessage = null;
      _pairingMessage = reactorText(ReactorText.addServerConnectingIdentity);
      _loading = true;
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
        _pairingMessage = reactorText(
          ReactorText.addServerInvalidPairingMessage,
        );
        _validationMessage = _pairingMessage;
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
        _pairingMessage = reactorText(
          ReactorText.addServerConnectionFailedMessage,
        );
      });
      ArcaneSonner.error(
        reactorText(ReactorText.addServerPairingFailed),
        description: e.toString(),
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
    final PairingCode? decoded = _decoded;
    final FleetController? fleet = FleetScope.of(context);
    final int savedCount = fleet?.fleetManager.servers.length ?? 0;
    final bool canPair = !_loading && decoded != null;
    final String? inputError = _validationMessage;
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
        dom.div(classes: 'reactor-add-layout', <Widget>[
          SectionPanel(
            label: reactorText(ReactorText.addServerPairingConsole),
            description: reactorText(
              ReactorText.addServerPairingConsoleDescription,
            ),
            trailing: _statusBadge(decoded, inputError),
            children: <Widget>[
              dom.div(classes: 'reactor-add-console', <Widget>[
                dom.div(classes: 'reactor-add-console-head', <Widget>[
                  dom.div(classes: 'reactor-add-console-title', <Widget>[
                    reactorStatusDot(
                      inputError != null
                          ? ReactorStatus.warning
                          : decoded == null
                          ? ReactorStatus.info
                          : ReactorStatus.healthy,
                      size: 7,
                    ),
                    dom.span(<Widget>[
                      Component.text(
                        reactorText(ReactorText.addServerHandshake),
                      ),
                    ]),
                  ]),
                  reactorEyebrow(
                    inputError != null
                        ? reactorText(ReactorText.addServerNeedsFullCode)
                        : decoded == null
                        ? reactorText(ReactorText.addServerStandby)
                        : reactorText(ReactorText.addServerDecoded),
                  ),
                ]),
                dom.div(classes: 'reactor-add-console-body', <Widget>[
                  TextInput(
                    placeholder: reactorText(
                      ReactorText.addServerInputPlaceholder,
                    ),
                    value: _code,
                    onChange: _onCodeChanged,
                    onSubmit: _onPair,
                    error: inputError,
                    helperText: reactorText(ReactorText.addServerInputHelper),
                    fullWidth: true,
                  ),
                  if (_pairingMessage != null)
                    dom.div(
                      classes: inputError == null
                          ? 'reactor-add-message info'
                          : 'reactor-add-message warning',
                      <Widget>[Component.text(_pairingMessage!)],
                    ),
                  _connectionDetails(decoded),
                ]),
              ]),
            ],
          ),
          dom.div(classes: 'reactor-add-side', <Widget>[
            SectionPanel(
              label: reactorText(ReactorText.addServerConnectionFlow),
              description: reactorText(
                ReactorText.addServerConnectionFlowDescription,
              ),
              children: <Widget>[
                dom.div(classes: 'reactor-add-step-list', <Widget>[
                  _flowStep(
                    '01',
                    reactorText(ReactorText.addServerCopy),
                    reactorText(ReactorText.addServerCopyDescription),
                  ),
                  _flowStep(
                    '02',
                    reactorText(ReactorText.addServerDecode),
                    reactorText(ReactorText.addServerDecodeDescription),
                  ),
                  _flowStep(
                    '03',
                    reactorText(ReactorText.addServerMonitor),
                    reactorText(ReactorText.addServerMonitorDescription),
                  ),
                ]),
              ],
            ),
            SectionPanel(
              label: reactorText(ReactorText.addServerSecurity),
              description: reactorText(
                ReactorText.addServerSecurityDescription,
              ),
              trailing: reactorStatusDot(
                ReactorStatus.info,
                label: reactorText(ReactorText.addServerCredentialScope),
              ),
              children: <Widget>[
                _detail(
                  reactorText(ReactorText.addServerSavedServers),
                  savedCount.toString(),
                ),
                _detail(reactorText(ReactorText.addServerFormat), 'RCT2'),
                _detail(
                  reactorText(ReactorText.addServerToken),
                  decoded == null
                      ? reactorText(ReactorText.addServerHiddenUntilDecoded)
                      : 'Bearer',
                ),
                _detail(
                  reactorText(ReactorText.addServerTransport),
                  decoded?.relayUrl.isEmpty ?? true
                      ? reactorText(ReactorText.addServerDirectHost)
                      : reactorText(ReactorText.addServerRelayChannel),
                ),
              ],
            ),
          ]),
        ]),
      ],
    );
  }

  Widget _statusBadge(PairingCode? decoded, String? inputError) {
    if (inputError != null) {
      return reactorBadge(
        reactorText(ReactorText.addServerCheckCode),
        ReactorStatus.warning,
      );
    }
    if (decoded == null) {
      return reactorBadge(
        reactorText(ReactorText.addServerAwaitingCode),
        ReactorStatus.info,
      );
    }
    return reactorBadge(
      reactorText(ReactorText.addServerCodeReady),
      ReactorStatus.healthy,
    );
  }

  Widget _connectionDetails(PairingCode? decoded) {
    if (decoded == null) {
      return dom.div(classes: 'reactor-add-detail-grid', <Widget>[
        _detail(
          reactorText(ReactorText.addServerStatus),
          reactorText(ReactorText.addServerWaitingForCode),
        ),
        _detail(reactorText(ReactorText.addServerExpected), 'RCT2. payload'),
        _detail(
          reactorText(ReactorText.addServerValidation),
          reactorText(ReactorText.addServerLocalDecode),
        ),
        _detail(
          reactorText(ReactorText.addServerHandshake),
          reactorText(ReactorText.addServerOneServer),
        ),
      ]);
    }
    return dom.div(classes: 'reactor-add-detail-grid', <Widget>[
      _detail(
        reactorText(ReactorText.addServerHost),
        decoded.directUrl.isEmpty
            ? reactorText(ReactorText.addServerRelayOnly)
            : Uri.parse(decoded.directUrl).host,
      ),
      _detail(
        reactorText(ReactorText.addServerPort),
        decoded.directUrl.isEmpty
            ? 'Relay'
            : Uri.parse(decoded.directUrl).hasPort
            ? Uri.parse(decoded.directUrl).port.toString()
            : Uri.parse(decoded.directUrl).scheme == 'https'
            ? '443'
            : '80',
      ),
      _detail(
        reactorText(ReactorText.addServerFingerprint),
        decoded.fingerprint.substring(0, 16),
      ),
      _detail(
        reactorText(ReactorText.addServerRelay),
        decoded.relayUrl.isEmpty
            ? reactorText(ReactorText.addServerNotUsed)
            : reactorText(ReactorText.commonEnabled),
      ),
    ]);
  }

  Widget _detail(String label, String value) {
    return dom.div(classes: 'reactor-add-detail', <Widget>[
      dom.span(classes: 'reactor-add-detail-label', <Widget>[
        Component.text(label),
      ]),
      dom.span(classes: 'reactor-add-detail-value', <Widget>[
        Component.text(value),
      ]),
    ]);
  }

  Widget _flowStep(String label, String title, String copy) {
    return dom.div(classes: 'reactor-add-step', <Widget>[
      dom.span(classes: 'reactor-add-step-label', <Widget>[
        Component.text('$label / $title'),
      ]),
      dom.div(classes: 'reactor-add-step-copy', <Widget>[Component.text(copy)]),
    ]);
  }
}
