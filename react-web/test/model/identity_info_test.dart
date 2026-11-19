import 'package:react_web/model/identity_info.dart';
import 'package:test/test.dart';

void main() {
  test('a server without platform fields is a Bukkit plugin host', () {
    final IdentityInfo info = IdentityInfo.fromJson(<String, dynamic>{
      'serverName': 'Lobby',
      'folia': false,
      'serverId': '127.0.0.1:25565',
    });
    expect(info.platform, 'bukkit');
    expect(info.ownerNoun, 'plugin');
    expect(info.modded, isFalse);
  });

  test('a modded server says mod and names its loader', () {
    final IdentityInfo info = IdentityInfo.fromJson(<String, dynamic>{
      'serverName': 'Pack',
      'folia': false,
      'serverId': '127.0.0.1:25565',
      'platform': 'modded',
      'loader': 'NeoForge',
      'ownerNoun': 'mod',
    });
    expect(info.loader, 'NeoForge');
    expect(info.modded, isTrue);
    expect(IdentityInfo.fromJson(info.toJson()).ownerNoun, 'mod');
  });
}
