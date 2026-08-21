package art.arcane.react.web;

import art.arcane.react.api.web.ActionParamCoercer;
import art.arcane.react.content.action.ActionCollectGarbage;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ActionParamCoercerTest {

    @Test
    void coerce_integer_value_sets_field() {
        ActionParamCoercer coercer = new ActionParamCoercer();
        ActionCollectGarbage action = new ActionCollectGarbage();
        ActionCollectGarbage.Params params = (ActionCollectGarbage.Params) coercer.coerce(action, Map.of("postGcWaitTicks", 7));
        assertEquals(7, params.getPostGcWaitTicks());
    }

    @Test
    void coerce_string_integer_parses_and_sets_field() {
        ActionParamCoercer coercer = new ActionParamCoercer();
        ActionCollectGarbage action = new ActionCollectGarbage();
        ActionCollectGarbage.Params params = (ActionCollectGarbage.Params) coercer.coerce(action, Map.of("postGcWaitTicks", "9"));
        assertEquals(9, params.getPostGcWaitTicks());
    }

    @Test
    void coerce_unknown_key_returns_defaults_unchanged() {
        ActionParamCoercer coercer = new ActionParamCoercer();
        ActionCollectGarbage action = new ActionCollectGarbage();
        ActionCollectGarbage.Params params = (ActionCollectGarbage.Params) coercer.coerce(action, Map.of("unknownKey", 1));
        assertEquals(2, params.getPostGcWaitTicks());
    }
}
