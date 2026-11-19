package art.arcane.react.platform;

public enum PlatformCapability {
  CONSOLE_COMMANDS("console commands"),
  PLAYERS("player lookup"),
  WORLDS("world lookup"),
  REGION_SCHEDULING("region scheduling"),
  ENTITY_SCHEDULING("entity scheduling");

  private final String label;

  PlatformCapability(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }
}
