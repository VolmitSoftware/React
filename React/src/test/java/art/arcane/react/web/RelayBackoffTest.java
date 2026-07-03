package art.arcane.react.web;

import art.arcane.react.api.web.relay.RelayBackoff;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RelayBackoffTest {

    @Test
    void attempt0ReturnsBaseMs() {
        RelayBackoff backoff = new RelayBackoff(1000, 30000);
        assertEquals(1000L, backoff.delayForAttempt(0));
    }

    @Test
    void doublesEachAttempt() {
        RelayBackoff backoff = new RelayBackoff(1000, 30000);
        assertEquals(2000L, backoff.delayForAttempt(1));
        assertEquals(4000L, backoff.delayForAttempt(2));
    }

    @Test
    void cappedAtCapMs() {
        RelayBackoff backoff = new RelayBackoff(1000, 30000);
        assertEquals(30000L, backoff.delayForAttempt(5));
        assertEquals(30000L, backoff.delayForAttempt(6));
        assertEquals(30000L, backoff.delayForAttempt(100));
    }

    @Test
    void largeAttemptNoOverflowOrNegative() {
        RelayBackoff backoff = new RelayBackoff(1000, 30000);
        long result = backoff.delayForAttempt(40);
        assertEquals(30000L, result);
        assertTrue(result > 0);
    }
}
