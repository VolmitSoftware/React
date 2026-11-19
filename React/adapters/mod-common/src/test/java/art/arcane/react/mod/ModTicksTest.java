package art.arcane.react.mod;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModTicksTest {
    private final List<String> warnings = new ArrayList<>();
    private final ModTicks ticks = new ModTicks(warnings::add);

    @Test
    void handedOverWorkRunsOnceAtTheNextTickBeforeRepeatingTasks() {
        List<String> order = new ArrayList<>();
        ticks.every(() -> order.add("repeat"));
        assertTrue(ticks.later(() -> order.add("later")));
        assertTrue(order.isEmpty());
        ticks.tick();
        assertEquals(List.of("later", "repeat"), order);
        ticks.tick();
        assertEquals(List.of("later", "repeat", "repeat"), order);
    }

    @Test
    void workQueuedDuringATickWaitsForTheNextOne() {
        List<String> order = new ArrayList<>();
        ticks.later(() -> {
            order.add("first");
            ticks.later(() -> order.add("second"));
        });
        ticks.tick();
        assertEquals(List.of("first"), order);
        ticks.tick();
        assertEquals(List.of("first", "second"), order);
    }

    @Test
    void delayedWorkRunsAfterItsDelayInTicks() {
        List<String> order = new ArrayList<>();
        assertTrue(ticks.later(() -> order.add("three"), 3));
        assertTrue(ticks.later(() -> order.add("one"), 1));
        ticks.tick();
        assertEquals(List.of("one"), order);
        ticks.tick();
        assertEquals(List.of("one"), order);
        ticks.tick();
        assertEquals(List.of("one", "three"), order);
    }

    @Test
    void closingDropsDelayedWork() {
        List<String> order = new ArrayList<>();
        ticks.later(() -> order.add("late"), 2);
        ticks.close();
        assertFalse(ticks.later(() -> order.add("refused"), 1));
        ticks.tick();
        ticks.tick();
        assertTrue(order.isEmpty());
    }

    @Test
    void closingARepeatingHandleStopsIt() throws Exception {
        List<String> order = new ArrayList<>();
        AutoCloseable handle = ticks.every(() -> order.add("repeat"));
        ticks.tick();
        handle.close();
        ticks.tick();
        assertEquals(List.of("repeat"), order);
    }

    @Test
    void aFailingTaskIsReportedAndKeepsItsSlot() {
        List<String> order = new ArrayList<>();
        ticks.every(() -> {
            order.add("fail");
            throw new IllegalStateException("boom");
        });
        ticks.every(() -> order.add("next"));
        ticks.tick();
        ticks.tick();
        assertEquals(List.of("fail", "next", "fail", "next"), order);
        assertEquals(2, warnings.size());
        assertTrue(warnings.getFirst().contains("boom"));
    }

    @Test
    void closedTicksRefuseWorkAndRunNothing() {
        List<String> order = new ArrayList<>();
        ticks.every(() -> order.add("repeat"));
        ticks.close();
        assertFalse(ticks.later(() -> order.add("later")));
        assertThrows(IllegalStateException.class, () -> ticks.every(() -> order.add("again")));
        ticks.tick();
        assertTrue(order.isEmpty());
    }
}
