package art.arcane.react.mod.cost;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModOwnersTest {
    @Test
    void costOwnersListsEveryLoadedModFoldedIntoItsOwner() {
        ModOwners owners = new ModOwners() {
            @Override
            public String ownerOf(Class<?> type) {
                return UNKNOWN;
            }

            @Override
            public String ownerOfNamespace(String namespace) {
                return namespace.startsWith("fabric-") ? "fabric-api" : null;
            }

            @Override
            public String displayName(String ownerId) {
                return "Name " + ownerId;
            }

            @Override
            public List<LoadedMod> loadedMods() {
                return List.of(
                        new LoadedMod("minecraft", "Minecraft", "26.3"),
                        new LoadedMod("fabric-api-base", "Fabric API Base", "1"),
                        new LoadedMod("fabric-networking-api-v1", "Fabric Networking", "1"),
                        new LoadedMod("react", "React", "2"));
            }
        };

        assertEquals(Map.of("minecraft", "Name minecraft", "fabric-api", "Name fabric-api", "react", "Name react"),
                owners.costOwners());
    }
}
