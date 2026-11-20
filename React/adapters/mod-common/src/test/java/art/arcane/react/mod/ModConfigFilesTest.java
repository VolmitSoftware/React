package art.arcane.react.mod;

import art.arcane.react.api.rendering.MapSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModConfigFilesTest {
    @TempDir
    Path folder;

    @Test
    void missingFileIsWrittenWithBukkitDocumentationComments() throws IOException {
        File file = folder.resolve("core/map.toml").toFile();
        List<String> messages = new ArrayList<>();

        MapSettings loaded = ModConfigFiles.load(file, MapSettings.class, new MapSettings(), "core:map", true, messages::add);

        String written = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        assertEquals(150L, loaded.getHeldMapRedrawIntervalMs());
        assertTrue(written.startsWith("# React configuration - core:map\n"), written);
        assertTrue(written.contains("# Minimum interval between canvas redraws for held React maps in milliseconds."), written);
        assertTrue(written.contains("heldMapRedrawIntervalMs = 150"), written);
        assertEquals(1, messages.size());
    }

    @Test
    void editedValuesAreKeptAndMissingKeysFilledIn() throws IOException {
        File file = folder.resolve("map.toml").toFile();
        Files.writeString(file.toPath(), "heldMapRedrawIntervalMs = 900\n", StandardCharsets.UTF_8);

        MapSettings loaded = ModConfigFiles.load(file, MapSettings.class, new MapSettings(), "core:map", false, (String message) -> {
        });

        String written = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        assertEquals(900L, loaded.getHeldMapRedrawIntervalMs());
        assertEquals(32, loaded.getMegamapMaxTiles());
        assertTrue(written.contains("heldMapRedrawIntervalMs = 900"), written);
        assertTrue(written.contains("megamapMaxTiles = 32"), written);
    }

    @Test
    void unreadableFileIsBackedUpAndResetWhenAllowed() throws IOException {
        File file = folder.resolve("map.toml").toFile();
        Files.writeString(file.toPath(), "heldMapRedrawIntervalMs = = 1\n", StandardCharsets.UTF_8);

        MapSettings loaded = ModConfigFiles.load(file, MapSettings.class, new MapSettings(), "core:map", true, (String message) -> {
        });

        assertEquals(150L, loaded.getHeldMapRedrawIntervalMs());
        assertEquals("heldMapRedrawIntervalMs = = 1\n", Files.readString(folder.resolve("map.toml.bak"), StandardCharsets.UTF_8));
    }
}
