package art.arcane.react.web;

import art.arcane.react.api.web.IncidentTimeline;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IncidentTimelineTest {

    @Test
    void boundedRingDropsOldestWhenCapacityExceeded() {
        IncidentTimeline timeline = new IncidentTimeline(3);
        timeline.record("a");
        timeline.record("b");
        timeline.record("c");
        timeline.record("d");
        timeline.record("e");

        assertEquals(3, timeline.size());

        List<String> recent10 = timeline.recent(10);
        assertEquals(3, recent10.size());

        assertTrue(recent10.get(0).contains("c"), "first of recent-3 must contain 'c': " + recent10.get(0));
        assertTrue(recent10.get(1).contains("d"), "second of recent-3 must contain 'd': " + recent10.get(1));
        assertTrue(recent10.get(2).contains("e"), "third of recent-3 must contain 'e': " + recent10.get(2));
    }

    @Test
    void recentNReturnsLastNInChronologicalOrder() {
        IncidentTimeline timeline = new IncidentTimeline(3);
        timeline.record("a");
        timeline.record("b");
        timeline.record("c");
        timeline.record("d");
        timeline.record("e");

        List<String> recent2 = timeline.recent(2);
        assertEquals(2, recent2.size());
        assertTrue(recent2.get(0).contains("d"), "first of recent-2 must contain 'd': " + recent2.get(0));
        assertTrue(recent2.get(1).contains("e"), "second of recent-2 must contain 'e': " + recent2.get(1));
    }

    @Test
    void recentZeroReturnsEmpty() {
        IncidentTimeline timeline = new IncidentTimeline(3);
        timeline.record("x");
        assertEquals(0, timeline.recent(0).size());
    }

    @Test
    void recordOnEmptyThenRecentOneReturnsIt() {
        IncidentTimeline timeline = new IncidentTimeline(3);
        timeline.record("hello");
        List<String> result = timeline.recent(1);
        assertEquals(1, result.size());
        assertTrue(result.get(0).contains("hello"), "entry must contain 'hello': " + result.get(0));
    }

    @Test
    void globalReturnsSameInstanceAcrossMultipleCalls() {
        IncidentTimeline first = IncidentTimeline.global();
        IncidentTimeline second = IncidentTimeline.global();
        assertSame(first, second);
    }
}
