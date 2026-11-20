class IdentityInfo {
  final String serverName;
  final bool folia;
  final String serverId;

  /// `bukkit`, `modded` or `proxy`; servers before this field are Bukkit.
  final String platform;

  /// The server software, for example Paper, Fabric or NeoForge.
  final String loader;

  /// What the server calls an add-on: `plugin` or `mod`.
  final String ownerNoun;

  const IdentityInfo({
    required this.serverName,
    required this.folia,
    required this.serverId,
    this.platform = 'bukkit',
    this.loader = '',
    this.ownerNoun = 'plugin',
  });

  bool get modded => ownerNoun == 'mod';

  bool get proxy => platform == 'proxy';

  factory IdentityInfo.fromJson(Map<String, dynamic> json) {
    return IdentityInfo(
      serverName: json['serverName'] as String,
      folia: json['folia'] as bool,
      serverId: json['serverId'] as String,
      platform: json['platform'] as String? ?? 'bukkit',
      loader: json['loader'] as String? ?? '',
      ownerNoun: json['ownerNoun'] as String? ?? 'plugin',
    );
  }

  Map<String, dynamic> toJson() => <String, dynamic>{
    'serverName': serverName,
    'folia': folia,
    'serverId': serverId,
    'platform': platform,
    'loader': loader,
    'ownerNoun': ownerNoun,
  };
}
