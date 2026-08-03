package art.arcane.react.api.monitor.configuration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MonitorGroupColorTest {

    @Test
    void nullColorFallsBackToValidHex() {
        MonitorGroup group = MonitorGroup.builder().name("test").build();
        String color = group.getColor();
        assertTrue(color.startsWith("#"));
        assertEquals(7, color.length());
    }

    @Test
    void blankColorFallsBackToValidHex() {
        MonitorGroup group = MonitorGroup.builder().name("test").color(" ").build();
        String color = group.getColor();
        assertTrue(color.startsWith("#"));
        assertEquals(7, color.length());
    }

    @Test
    void explicitColorIsPreserved() {
        MonitorGroup group = MonitorGroup.builder().name("test").color("#123456").build();
        assertEquals("#123456", group.getColor());
    }

    @Test
    void colorValueDecodesFallbackWithoutThrowing() {
        MonitorGroup group = MonitorGroup.builder().name("test").build();
        assertNotEquals(0, group.getColorValue());
    }
}
