package art.arcane.react.mod;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModPermissionsTest {
    @Test
    void nodesNameTheirPermissionAtom() {
        assertEquals(Optional.of("react:use"), ModPermissions.atom("react.use"));
        assertEquals(Optional.of("react:shorthands.gms"), ModPermissions.atom("react.shorthands.gms"));
    }

    @Test
    void wildcardsAndMalformedNodesNameNoAtom() {
        assertEquals(Optional.empty(), ModPermissions.atom("react.*"));
        assertEquals(Optional.empty(), ModPermissions.atom("react"));
        assertEquals(Optional.empty(), ModPermissions.atom(null));
    }
}
