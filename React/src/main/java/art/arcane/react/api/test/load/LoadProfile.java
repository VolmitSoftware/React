package art.arcane.react.api.test.load;

public record LoadProfile(
    int mobHerds,
    int mobsPerHerd,
    int hopperNetworks,
    int fallingBlocks,
    int tntBursts,
    int itemFloodPerTick,
    int redstoneClocks
) {
  public static LoadProfile forPlayers(int players) {
    int scale = Math.max(1, players);
    return new LoadProfile(
        Math.max(1, scale / 50),
        20,
        Math.max(1, scale / 100),
        Math.max(4, scale / 20),
        Math.max(1, scale / 200),
        Math.max(1, scale / 100),
        Math.max(1, scale / 200)
    );
  }
}
