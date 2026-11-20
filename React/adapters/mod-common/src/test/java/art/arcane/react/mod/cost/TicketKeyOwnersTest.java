package art.arcane.react.mod.cost;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TicketKeyOwnersTest {
    @SuppressWarnings("unused")
    private static final class ForgeOwner {
        private final String modId;
        private final Object owner = new Object();

        private ForgeOwner(String modId) {
            this.modId = modId;
        }
    }

    @SuppressWarnings("unused")
    private static final class NeoOwner {
        private final Object id;

        private NeoOwner(Object id) {
            this.id = id;
        }
    }

    private record Location(String namespace, String path) {
        @Override
        public String toString() {
            return namespace + ":" + path;
        }
    }

    @Test
    void namespaceOf_readsForgeModId() {
        assertEquals("mekanism", TicketKeyOwners.namespaceOf(new ForgeOwner("mekanism")));
    }

    @Test
    void namespaceOf_readsNeoForgeControllerNamespace() {
        assertEquals("ae2", TicketKeyOwners.namespaceOf(new NeoOwner(new Location("ae2", "spatial"))));
    }

    @Test
    void namespaceOf_ignoresKeysWithoutAnOwner() {
        assertNull(TicketKeyOwners.namespaceOf(null));
        assertNull(TicketKeyOwners.namespaceOf(42L));
        assertNull(TicketKeyOwners.namespaceOf(new NeoOwner("no-namespace")));
    }

    @Test
    void namespace_splitsNamespacedNames() {
        assertEquals("forge", TicketKeyOwners.namespace("forge:block"));
        assertNull(TicketKeyOwners.namespace("forced"));
        assertNull(TicketKeyOwners.namespace(":odd"));
    }
}
