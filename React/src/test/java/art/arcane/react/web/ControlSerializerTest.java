package art.arcane.react.web;

import art.arcane.react.api.web.ControlSerializer;
import art.arcane.react.api.web.dto.ControlItemDto;
import art.arcane.react.api.web.dto.KnobDto;
import art.arcane.react.content.feature.FeaturePerWorldTickBudget;
import art.arcane.react.content.tweak.TweakFastDrops;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ControlSerializerTest {

    @Test
    void featureDtoCarriesIdentityMetadataAndKnobsWithoutEnabledKnob() {
        ControlSerializer serializer = new ControlSerializer();
        FeaturePerWorldTickBudget feature = new FeaturePerWorldTickBudget();
        ControlItemDto dto = serializer.toDto(feature);

        assertEquals("per-world-tick-budget", dto.id);
        assertEquals("Per World Tick Budget", dto.name);
        assertTrue(dto.enabled);
        assertNotNull(dto.description);
        assertFalse(dto.description.isBlank());
        assertNotNull(dto.category);
        assertFalse(dto.category.isBlank());

        KnobDto budgetMs = findKnob(dto.knobs, "budgetMs");
        assertNotNull(budgetMs, "knob budgetMs missing");
        assertEquals("double", budgetMs.type);
        assertEquals(35.0, ((Number) budgetMs.value).doubleValue(), 0.0001);

        assertNotNull(findKnob(dto.knobs, "panicMs"), "knob panicMs missing");
        assertNotNull(findKnob(dto.knobs, "releaseMs"), "knob releaseMs missing");

        KnobDto tickIntervalMs = findKnob(dto.knobs, "tickIntervalMS");
        assertNotNull(tickIntervalMs, "knob tickIntervalMS missing");
        assertEquals("int", tickIntervalMs.type);

        assertNull(findKnob(dto.knobs, "enabled"), "enabled must not appear as a knob");
    }

    @Test
    void tweakDtoCarriesIdentityMetadataAndIsEnabledByDefault() {
        ControlSerializer serializer = new ControlSerializer();
        TweakFastDrops tweak = new TweakFastDrops();
        ControlItemDto dto = serializer.toDto(tweak);

        assertEquals("fast-drops", dto.id);
        assertEquals("Fast Drops", dto.name);
        assertTrue(dto.enabled);
    }

    private static KnobDto findKnob(KnobDto[] knobs, String key) {
        if (knobs == null) {
            return null;
        }
        for (KnobDto knob : knobs) {
            if (key.equals(knob.key)) {
                return knob;
            }
        }
        return null;
    }
}
