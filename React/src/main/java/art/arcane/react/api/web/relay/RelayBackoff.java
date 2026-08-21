package art.arcane.react.api.web.relay;

public final class RelayBackoff {

    private final long baseMs;
    private final long capMs;

    public RelayBackoff(long baseMs, long capMs) {
        this.baseMs = baseMs;
        this.capMs = capMs;
    }

    public long delayForAttempt(int attempt) {
        if (attempt >= 31) {
            return capMs;
        }
        long shifted = baseMs * (1L << attempt);
        return Math.min(capMs, shifted);
    }
}
