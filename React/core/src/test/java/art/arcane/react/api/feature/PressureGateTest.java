package art.arcane.react.api.feature;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PressureGateTest {

    private static PressureGate engagedAt(long nowMs, long sustainEngageMs) {
        PressureGate gate = new PressureGate();
        assertFalse(gate.update(nowMs - sustainEngageMs, true, false, sustainEngageMs, sustainEngageMs));
        assertTrue(gate.update(nowMs, true, false, sustainEngageMs, sustainEngageMs));
        return gate;
    }

    @Test
    void engagesOnlyAfterSustainedPressureReachesThreshold() {
        PressureGate gate = new PressureGate();
        assertFalse(gate.update(1_000L, true, false, 5_000L, 5_000L));
        assertFalse(gate.isEngaged());
        assertFalse(gate.update(3_000L, true, false, 5_000L, 5_000L));
        assertFalse(gate.update(5_999L, true, false, 5_000L, 5_000L));
        assertFalse(gate.isEngaged());
        assertTrue(gate.update(6_000L, true, false, 5_000L, 5_000L));
        assertTrue(gate.isEngaged());
    }

    @Test
    void firstPressuredEvaluationOnlyArmsTimerEvenWithZeroSustain() {
        PressureGate gate = new PressureGate();
        assertFalse(gate.update(1_000L, true, false, 0L, 0L));
        assertFalse(gate.isEngaged());
        assertTrue(gate.update(1_001L, true, false, 0L, 0L));
        assertTrue(gate.isEngaged());
    }

    @Test
    void releasesOnlyAfterSustainedCalmReachesThreshold() {
        PressureGate gate = engagedAt(10_000L, 5_000L);
        assertTrue(gate.update(11_000L, false, true, 5_000L, 8_000L));
        assertTrue(gate.isEngaged());
        assertTrue(gate.update(18_999L, false, true, 5_000L, 8_000L));
        assertTrue(gate.isEngaged());
        assertFalse(gate.update(19_000L, false, true, 5_000L, 8_000L));
        assertFalse(gate.isEngaged());
    }

    @Test
    void firstCalmEvaluationOnlyArmsTimerEvenWithZeroSustain() {
        PressureGate gate = engagedAt(10_000L, 5_000L);
        assertTrue(gate.update(11_000L, false, true, 0L, 0L));
        assertTrue(gate.isEngaged());
        assertFalse(gate.update(11_001L, false, true, 0L, 0L));
        assertFalse(gate.isEngaged());
    }

    @Test
    void flappingBetweenPressureAndCalmNeverEngages() {
        PressureGate gate = new PressureGate();
        long now = 1_000L;
        for (int i = 0; i < 200; i++) {
            boolean pressured = i % 2 == 0;
            gate.update(now, pressured, !pressured, 5_000L, 5_000L);
            assertFalse(gate.isEngaged());
            now += 60_000L;
        }
    }

    @Test
    void pressureTimerResetsWhenPressureDrops() {
        PressureGate gate = new PressureGate();
        assertFalse(gate.update(1_000L, true, false, 5_000L, 5_000L));
        assertFalse(gate.update(2_000L, false, true, 5_000L, 5_000L));
        assertFalse(gate.update(10_000L, true, false, 5_000L, 5_000L));
        assertFalse(gate.update(14_999L, true, false, 5_000L, 5_000L));
        assertFalse(gate.isEngaged());
        assertTrue(gate.update(15_000L, true, false, 5_000L, 5_000L));
    }

    @Test
    void calmTimerResetsWhenPressureReturns() {
        PressureGate gate = engagedAt(10_000L, 5_000L);
        assertTrue(gate.update(11_000L, false, true, 5_000L, 5_000L));
        assertTrue(gate.update(12_000L, true, false, 5_000L, 5_000L));
        assertTrue(gate.update(20_000L, false, true, 5_000L, 5_000L));
        assertTrue(gate.update(24_999L, false, true, 5_000L, 5_000L));
        assertTrue(gate.isEngaged());
        assertFalse(gate.update(25_000L, false, true, 5_000L, 5_000L));
    }

    @Test
    void engagingResetsCalmTimerSoReleaseRequiresFreshSustain() {
        PressureGate gate = engagedAt(10_000L, 5_000L);
        assertTrue(gate.update(30_000L, false, true, 5_000L, 5_000L));
        assertTrue(gate.isEngaged());
        assertTrue(gate.update(34_999L, false, true, 5_000L, 5_000L));
        assertFalse(gate.update(35_000L, false, true, 5_000L, 5_000L));
    }

    @Test
    void releasingResetsPressureTimerSoReengageRequiresFreshSustain() {
        PressureGate gate = engagedAt(10_000L, 5_000L);
        assertTrue(gate.update(11_000L, false, true, 5_000L, 5_000L));
        assertFalse(gate.update(16_000L, false, true, 5_000L, 5_000L));
        assertFalse(gate.update(17_000L, true, false, 5_000L, 5_000L));
        assertFalse(gate.update(21_999L, true, false, 5_000L, 5_000L));
        assertTrue(gate.update(22_000L, true, false, 5_000L, 5_000L));
    }

    @Test
    void resetClearsEngagedState() {
        PressureGate gate = engagedAt(10_000L, 5_000L);
        assertTrue(gate.isEngaged());
        gate.reset();
        assertFalse(gate.isEngaged());
    }

    @Test
    void resetClearsPressureTimer() {
        PressureGate gate = new PressureGate();
        assertFalse(gate.update(1_000L, true, false, 5_000L, 5_000L));
        gate.reset();
        assertFalse(gate.update(20_000L, true, false, 5_000L, 5_000L));
        assertFalse(gate.isEngaged());
        assertFalse(gate.update(24_999L, true, false, 5_000L, 5_000L));
        assertTrue(gate.update(25_000L, true, false, 5_000L, 5_000L));
    }

    @Test
    void notPressuredStaysDisengagedAndNotCalmStaysEngaged() {
        PressureGate gate = new PressureGate();
        assertFalse(gate.update(1_000L, false, true, 5_000L, 5_000L));
        assertFalse(gate.isEngaged());
        PressureGate engaged = engagedAt(10_000L, 5_000L);
        assertTrue(engaged.update(60_000L, true, false, 5_000L, 5_000L));
        assertTrue(engaged.isEngaged());
    }

    @Test
    void negativeSustainBehavesAsZero() {
        PressureGate gate = new PressureGate();
        assertFalse(gate.update(1_000L, true, false, -5_000L, -5_000L));
        assertTrue(gate.update(1_001L, true, false, -5_000L, -5_000L));
        assertTrue(gate.update(2_000L, false, true, -5_000L, -5_000L));
        assertFalse(gate.update(2_001L, false, true, -5_000L, -5_000L));
    }
}
