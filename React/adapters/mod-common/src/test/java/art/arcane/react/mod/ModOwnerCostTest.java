package art.arcane.react.mod;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModOwnerCostTest {
    @Test
    void reactAndBlankOwnersGetNoSamplerLikeBukkit() {
        assertFalse(ModOwnerCost.sampled(ReactModRuntime.ID));
        assertFalse(ModOwnerCost.sampled(""));
        assertTrue(ModOwnerCost.sampled("minecraft"));
        assertTrue(ModOwnerCost.sampled("forge"));
    }
}
