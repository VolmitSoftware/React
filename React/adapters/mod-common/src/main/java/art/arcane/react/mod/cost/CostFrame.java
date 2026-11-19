package art.arcane.react.mod.cost;

/**
 * Exclusive-time accounting shared by every event wrapper and tick hook. Work nested inside a timed call is charged
 * to the inner owner and subtracted from the outer one. Calls off the server thread are only counted.
 */
public final class CostFrame {
    public static final long OFF_THREAD = Long.MIN_VALUE;
    private static final ThreadLocal<Frame> FRAMES = ThreadLocal.withInitial(Frame::new);

    private static final class Frame {
        private long[] parentChildNanos = new long[16];
        private int depth;
        private long childNanos;
    }

    private CostFrame() {
    }

    public static long enter() {
        if (Thread.currentThread() != CostRegistry.serverThread) {
            return OFF_THREAD;
        }
        Frame frame = FRAMES.get();
        if (frame.depth == frame.parentChildNanos.length) {
            frame.parentChildNanos = java.util.Arrays.copyOf(frame.parentChildNanos, frame.depth * 2);
        }
        frame.parentChildNanos[frame.depth++] = frame.childNanos;
        frame.childNanos = 0L;
        return System.nanoTime();
    }

    public static void exit(long start, CostBucket bucket) {
        if (start == OFF_THREAD) {
            bucket.asyncCalls.increment();
            return;
        }
        long total = System.nanoTime() - start;
        Frame frame = FRAMES.get();
        bucket.nanos.add(Math.max(0L, total - frame.childNanos));
        bucket.syncCalls.increment();
        frame.depth--;
        frame.childNanos = frame.depth == 0 ? 0L : frame.parentChildNanos[frame.depth] + total;
    }
}
