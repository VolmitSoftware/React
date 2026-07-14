package art.arcane.react.web;

import art.arcane.react.React;
import art.arcane.react.api.web.FeatureWorldBackend;
import art.arcane.react.api.web.dto.WorldDto;
import art.arcane.react.content.feature.FeaturePerWorldTickBudget;
import art.arcane.react.testutil.Fakes;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class FeatureWorldBackendTest {
    @Test
    void update_returns_null_for_unqualified_world_key() {
        FeatureWorldBackend backend = new FeatureWorldBackend();

        assertNull(backend.update("world", 40.0, null, null));
    }

    @Test
    void update_resolves_namespaced_world_key_with_slash() {
        FeatureWorldBackend backend = new FeatureWorldBackend();
        NamespacedKey worldKey = new NamespacedKey("test", "world/path");
        World world = Fakes.world("world");
        Mockito.when(world.getKey()).thenReturn(worldKey);

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
             MockedStatic<React> react = Mockito.mockStatic(React.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(null);
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));
            react.when(() -> React.feature(FeaturePerWorldTickBudget.ID)).thenReturn(null);

            WorldDto result = backend.update(worldKey.toString(), 40.0, null, null);

            assertNotNull(result);
            assertEquals("test:world/path", result.key);
            assertEquals("world", result.name);
        }
    }
}
