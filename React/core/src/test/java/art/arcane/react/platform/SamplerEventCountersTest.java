package art.arcane.react.platform;

import art.arcane.react.platform.SamplerEventCounters.Counter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SamplerEventCountersTest {
    @Test
    void countersWithoutListenersAreInactiveAndCountSafely() {
        SamplerEventCounters counters = SamplerEventCounters.get();
        for (Counter counter : Counter.values()) {
            assertFalse(counters.isActive(counter));
            assertDoesNotThrow(() -> counters.count(counter, null));
        }
    }
}
