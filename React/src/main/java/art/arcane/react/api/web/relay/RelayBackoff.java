package art.arcane.react.api.web.relay;

/**
 * Reconnect delays to the relay. The first {@code fastAttempts} tries wait about {@code fastMillis}, so a relay that
 * restarted within about 45 seconds is reached within a quarter second of coming back; a refused connect costs nothing.
 * After that the delay doubles from {@code minimumMillis} to {@code maximumMillis}. Each delay is drawn from the upper
 * half of its ceiling, so servers do not reconnect in step.
 */
public record RelayBackoff(long fastMillis, int fastAttempts, long minimumMillis, long maximumMillis) {
    public static final RelayBackoff DEFAULT = new RelayBackoff(250L, 240, 1000L, 30000L);

    public RelayBackoff {
        if (fastMillis < 1L || fastAttempts < 0 || minimumMillis < 1L || maximumMillis < minimumMillis) {
            throw new IllegalArgumentException("need fastMillis >= 1, fastAttempts >= 0 and 1 <= minimum <= maximum");
        }
    }

    /** The delay before reconnect attempt {@code attempt} (0 first), with {@code unit} in [0, 1] choosing within the half. */
    public long delayMillis(int attempt, double unit) {
        long ceiling;
        if (attempt < fastAttempts) {
            ceiling = fastMillis;
        } else {
            ceiling = minimumMillis;
            for (int step = fastAttempts; step < attempt && ceiling < maximumMillis; step++) {
                ceiling = Math.min(maximumMillis, ceiling * 2L);
            }
        }
        long half = ceiling / 2L;
        return half + (long) ((ceiling - half) * Math.max(0D, Math.min(unit, 1D)));
    }
}
