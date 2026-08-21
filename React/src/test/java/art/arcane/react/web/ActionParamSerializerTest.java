package art.arcane.react.web;

import art.arcane.react.api.web.ActionParamSerializer;
import art.arcane.react.api.web.dto.ActionDescriptorDto;
import art.arcane.react.api.web.dto.ActionParamDto;
import art.arcane.react.content.action.ActionCollectGarbage;
import art.arcane.react.content.action.ActionHopperNetworkNormalize;
import art.arcane.react.content.action.ActionPurgeEntities;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ActionParamSerializerTest {

    @Test
    void describe_collect_garbage_is_destructive_and_has_post_gc_wait_ticks() {
        ActionParamSerializer serializer = new ActionParamSerializer();
        ActionDescriptorDto dto = serializer.describe(new ActionCollectGarbage());
        assertTrue(dto.destructive);
        assertEquals("collect-garbage", dto.id);
        assertNotNull(dto.params);
        ActionParamDto param = paramByKey(dto.params, "postGcWaitTicks");
        assertNotNull(param);
        assertEquals("int", param.type);
        assertFalse(param.required);
        assertEquals(2, param.defaultValue);
    }

    @Test
    void describe_purge_entities_is_destructive_and_has_scalar_params_only() {
        ActionParamSerializer serializer = new ActionParamSerializer();
        ActionDescriptorDto dto = serializer.describe(new ActionPurgeEntities());
        assertTrue(dto.destructive);
        assertNotNull(dto.params);
        ActionParamDto secondsToPurge = paramByKey(dto.params, "secondsToPurge");
        assertNotNull(secondsToPurge, "secondsToPurge must be present");
        assertEquals("int", secondsToPurge.type);
        ActionParamDto defaultBlacklist = paramByKey(dto.params, "defaultBlacklist");
        assertNotNull(defaultBlacklist, "defaultBlacklist must be present");
        assertEquals("bool", defaultBlacklist.type);
        for (ActionParamDto p : dto.params) {
            assertFalse("area".equals(p.key), "non-scalar field 'area' must not be present");
            assertFalse("entityFilter".equals(p.key), "non-scalar field 'entityFilter' must not be present");
        }
    }

    @Test
    void describe_hopper_network_normalize_is_not_destructive() {
        ActionParamSerializer serializer = new ActionParamSerializer();
        ActionDescriptorDto dto = serializer.describe(new ActionHopperNetworkNormalize());
        assertFalse(dto.destructive);
    }

    @Test
    void action_param_dto_json_contains_default_and_required_keys() {
        ActionParamSerializer serializer = new ActionParamSerializer();
        ActionDescriptorDto dto = serializer.describe(new ActionCollectGarbage());
        ActionParamDto param = paramByKey(dto.params, "postGcWaitTicks");
        assertNotNull(param);
        String json = new Gson().toJson(param);
        assertTrue(json.contains("\"default\":"), "JSON must contain key 'default' not 'defaultValue', got: " + json);
        assertTrue(json.contains("\"required\":"), "JSON must contain key 'required', got: " + json);
    }

    private static ActionParamDto paramByKey(ActionParamDto[] params, String key) {
        if (params == null) {
            return null;
        }
        for (ActionParamDto p : params) {
            if (key.equals(p.key)) {
                return p;
            }
        }
        return null;
    }
}
