package art.arcane.react.web;

import art.arcane.react.api.web.relay.RelayBackoff;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RelayBackoffTest {

    @Test
    void triesInTheFirstThreeQuartersOfAMinuteComeFastSoARestartedRelayIsReachedWithinAQuarterSecond() {
        RelayBackoff backoff = RelayBackoff.DEFAULT;
        for (int attempt = 0; attempt < 240; attempt++) {
            assertEquals(125L, backoff.delayMillis(attempt, 0D));
            assertEquals(250L, backoff.delayMillis(attempt, 1D));
        }
    }

    @Test
    void afterTheFastTriesTheDelayDoublesToItsCap() {
        RelayBackoff backoff = RelayBackoff.DEFAULT;
        assertEquals(1000L, backoff.delayMillis(240, 1D));
        assertEquals(2000L, backoff.delayMillis(241, 1D));
        assertEquals(4000L, backoff.delayMillis(242, 1D));
        assertEquals(30000L, backoff.delayMillis(250, 1D));
        assertEquals(15000L, backoff.delayMillis(1000, 0D));
    }

    @Test
    void theUnitIsClamped() {
        RelayBackoff backoff = new RelayBackoff(100, 1, 1000, 8000);
        assertEquals(100L, backoff.delayMillis(0, 7D));
        assertEquals(50L, backoff.delayMillis(0, -3D));
        assertTrue(backoff.delayMillis(Integer.MAX_VALUE, 0.5D) <= 8000L);
    }

    @Test
    void invalidBoundsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> new RelayBackoff(0, 1, 1000, 8000));
        assertThrows(IllegalArgumentException.class, () -> new RelayBackoff(100, -1, 1000, 8000));
        assertThrows(IllegalArgumentException.class, () -> new RelayBackoff(100, 1, 9000, 8000));
    }
}
