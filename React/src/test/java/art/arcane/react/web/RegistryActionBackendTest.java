package art.arcane.react.web;

import art.arcane.react.api.action.Action;
import art.arcane.react.api.web.ActionParamSerializer;
import art.arcane.react.api.web.RegistryActionBackend;
import art.arcane.react.api.web.dto.ActionDescriptorDto;
import art.arcane.react.content.action.ActionCollectGarbage;
import art.arcane.react.content.action.ActionHopperNetworkNormalize;
import art.arcane.react.content.action.ActionUnknown;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RegistryActionBackendTest {

    private RegistryActionBackend backend;

    @BeforeEach
    void setUp() {
        ActionCollectGarbage gc = new ActionCollectGarbage();
        ActionHopperNetworkNormalize hopper = new ActionHopperNetworkNormalize();
        ActionUnknown unknown = new ActionUnknown();

        Collection<Action<?>> allActions = List.of(gc, hopper, unknown);
        Map<String, Action<?>> byId = Map.of(
                gc.getId(), gc,
                hopper.getId(), hopper,
                unknown.getId(), unknown
        );

        ActionParamSerializer serializer = new ActionParamSerializer();
        backend = new RegistryActionBackend(
                () -> allActions,
                id -> byId.get(id),
                serializer
        );
    }

    @Test
    void list_excludes_unknown_action_and_returns_real_actions() {
        List<ActionDescriptorDto> descriptors = backend.list();
        assertEquals(2, descriptors.size());
        boolean hasGc = descriptors.stream().anyMatch(d -> ActionCollectGarbage.ID.equals(d.id));
        boolean hasHopper = descriptors.stream().anyMatch(d -> ActionHopperNetworkNormalize.ID.equals(d.id));
        boolean hasUnknown = descriptors.stream().anyMatch(d -> ActionUnknown.ID.equals(d.id));
        assertTrue(hasGc, "collect-garbage must be present");
        assertTrue(hasHopper, "action-hopper-network-normalize must be present");
        assertTrue(!hasUnknown, "unknown action must be excluded");
    }

    @Test
    void get_collect_garbage_returns_destructive_descriptor() {
        ActionDescriptorDto dto = backend.get(ActionCollectGarbage.ID);
        assertNotNull(dto);
        assertEquals(ActionCollectGarbage.ID, dto.id);
        assertTrue(dto.destructive);
    }

    @Test
    void get_unknown_id_returns_null() {
        ActionDescriptorDto dto = backend.get("no-such-action");
        assertNull(dto);
    }

    @Test
    void get_unknown_action_id_returns_null() {
        ActionDescriptorDto dto = backend.get(ActionUnknown.ID);
        assertNull(dto);
    }
}
