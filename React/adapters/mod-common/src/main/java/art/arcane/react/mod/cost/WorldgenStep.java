package art.arcane.react.mod.cost;

/** The chunk generation steps React times as a whole, whichever mod's content runs inside them. */
public enum WorldgenStep {
    STRUCTURE_STARTS("structure-starts"),
    BIOMES("biomes"),
    NOISE("noise"),
    SURFACE("surface"),
    CARVERS("carvers"),
    FEATURES("features");

    private final String id;

    WorldgenStep(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
