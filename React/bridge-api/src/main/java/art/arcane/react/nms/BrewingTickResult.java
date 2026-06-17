package art.arcane.react.nms;

public final class BrewingTickResult {
    public static final BrewingTickResult RUN_VANILLA = new BrewingTickResult(false, 0);
    public static final BrewingTickResult SKIP = new BrewingTickResult(true, 0);

    private final boolean skip;
    private final int advanceTicks;

    private BrewingTickResult(boolean skip, int advanceTicks) {
        this.skip = skip;
        this.advanceTicks = advanceTicks;
    }

    public static BrewingTickResult runAndAdvance(int advanceTicks) {
        if (advanceTicks <= 0) {
            return RUN_VANILLA;
        }
        return new BrewingTickResult(false, advanceTicks);
    }

    public boolean skip() {
        return skip;
    }

    public int advanceTicks() {
        return advanceTicks;
    }
}
