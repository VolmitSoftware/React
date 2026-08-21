package art.arcane.react.nms;

public final class FurnaceTickResult {
    public static final FurnaceTickResult RUN_VANILLA = new FurnaceTickResult(false, 0);
    public static final FurnaceTickResult SKIP = new FurnaceTickResult(true, 0);

    private final boolean skip;
    private final int advanceTicks;

    private FurnaceTickResult(boolean skip, int advanceTicks) {
        this.skip = skip;
        this.advanceTicks = advanceTicks;
    }

    public static FurnaceTickResult runAndAdvance(int advanceTicks) {
        if (advanceTicks <= 0) {
            return RUN_VANILLA;
        }
        return new FurnaceTickResult(false, advanceTicks);
    }

    public boolean skip() {
        return skip;
    }

    public int advanceTicks() {
        return advanceTicks;
    }
}
