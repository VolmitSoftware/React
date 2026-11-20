package art.arcane.react.api.web;

import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class Log4jConsoleCaptureTest {
    @Test
    void infoAndMoreSevereRecordsAreCaptured() {
        Assertions.assertTrue(Log4jConsoleCapture.captured(Level.INFO, "net.minecraft.server.MinecraftServer"));
        Assertions.assertTrue(Log4jConsoleCapture.captured(Level.WARN, "React"));
        Assertions.assertTrue(Log4jConsoleCapture.captured(Level.ERROR, "mezz.jei"));
    }

    @Test
    void debugAndTraceRecordsAreSkipped() {
        Assertions.assertFalse(Log4jConsoleCapture.captured(Level.DEBUG, "net.minecraft.server.MinecraftServer"));
        Assertions.assertFalse(Log4jConsoleCapture.captured(Level.TRACE, "React"));
    }

    @Test
    void bundledWebLibrariesAreNeverCaptured() {
        Assertions.assertFalse(Log4jConsoleCapture.captured(Level.ERROR, "art.arcane.react.internal.jetty.io.WriteFlusher"));
        Assertions.assertFalse(Log4jConsoleCapture.captured(Level.INFO, "art.arcane.react.internal.javalin.Javalin"));
    }

    @Test
    void recordsWithoutALevelAreCaptured() {
        Assertions.assertTrue(Log4jConsoleCapture.captured(null, "React"));
        Assertions.assertTrue(Log4jConsoleCapture.captured(Level.INFO, null));
    }
}
