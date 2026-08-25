library;

final class OnlinePlayerInfo {
  final String id;
  final String name;

  const OnlinePlayerInfo({required this.id, required this.name});

  factory OnlinePlayerInfo.fromJson(Map<String, dynamic> json) =>
      OnlinePlayerInfo(id: json['id'] as String, name: json['name'] as String);
}

final class PlayerTeleportResult {
  final String playerId;
  final String playerName;
  final String status;
  final String worldKey;
  final int blockX;
  final int blockZ;

  const PlayerTeleportResult({
    required this.playerId,
    required this.playerName,
    required this.status,
    required this.worldKey,
    required this.blockX,
    required this.blockZ,
  });

  factory PlayerTeleportResult.fromJson(Map<String, dynamic> json) =>
      PlayerTeleportResult(
        playerId: json['playerId'] as String,
        playerName: json['playerName'] as String,
        status: json['status'] as String,
        worldKey: json['worldKey'] as String,
        blockX: (json['blockX'] as num).toInt(),
        blockZ: (json['blockZ'] as num).toInt(),
      );
}
