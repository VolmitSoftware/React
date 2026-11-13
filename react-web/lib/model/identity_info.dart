class IdentityInfo {
  final String serverName;
  final bool folia;
  final String serverId;

  const IdentityInfo({
    required this.serverName,
    required this.folia,
    required this.serverId,
  });

  factory IdentityInfo.fromJson(Map<String, dynamic> json) {
    return IdentityInfo(
      serverName: json['serverName'] as String,
      folia: json['folia'] as bool,
      serverId: json['serverId'] as String,
    );
  }

  Map<String, dynamic> toJson() => <String, dynamic>{
    'serverName': serverName,
    'folia': folia,
    'serverId': serverId,
  };
}
