package art.arcane.react.mod.cost;

import java.util.Locale;

public enum CostKind {
    EVENT,
    BLOCK_ENTITY,
    ENTITY,
    WORLDGEN;

    private final String id = name().toLowerCase(Locale.ROOT);

    /** The kind id the shared owner cost model and React Web use. */
    public String id() {
        return id;
    }
}
