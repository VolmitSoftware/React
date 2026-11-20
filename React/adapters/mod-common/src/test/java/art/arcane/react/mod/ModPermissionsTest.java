package art.arcane.react.mod;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void nodesAreGrantedByTheirEnclosingWildcards() {
        assertEquals(List.of("react:shorthands.gms", "react:shorthands.all", "react:all"), ModPermissions.atoms("react.shorthands.gms"));
        assertEquals(List.of("react:use", "react:all"), ModPermissions.atoms("react.use"));
        assertEquals(List.of("react:shorthands.all", "react:all"), ModPermissions.atoms("react.shorthands.*"));
        assertEquals(List.of("react:all"), ModPermissions.atoms("react.*"));
        assertEquals(List.of(), ModPermissions.atoms(null));
    }

    @Test
    void singleplayerOwnerHoldsEveryNode() {
        assertTrue(ModPermissions.worldOwner(false, true));
    }

    @Test
    void otherLanPlayersAreNotWorldOwners() {
        assertFalse(ModPermissions.worldOwner(false, false));
    }

    @Test
    void dedicatedServersHaveNoWorldOwner() {
        assertFalse(ModPermissions.worldOwner(true, true));
        assertFalse(ModPermissions.worldOwner(true, false));
    }
}
