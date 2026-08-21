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
    void featureId_isPerWorldTickBudget() {
        ControlSerializer serializer = new ControlSerializer();
        FeaturePerWorldTickBudget feature = new FeaturePerWorldTickBudget();
        ControlItemDto dto = serializer.toDto(feature);
        assertEquals("per-world-tick-budget", dto.id);
    }

    @Test
    void featureName_isCapitalized() {
        ControlSerializer serializer = new ControlSerializer();
        FeaturePerWorldTickBudget feature = new FeaturePerWorldTickBudget();
        ControlItemDto dto = serializer.toDto(feature);
        assertEquals("Per World Tick Budget", dto.name);
    }

    @Test
    void featureEnabled_isTrueByDefault() {
        ControlSerializer serializer = new ControlSerializer();
        FeaturePerWorldTickBudget feature = new FeaturePerWorldTickBudget();
        ControlItemDto dto = serializer.toDto(feature);
        assertTrue(dto.enabled);
    }

    @Test
    void featureDescription_isNonEmpty() {
        ControlSerializer serializer = new ControlSerializer();
        FeaturePerWorldTickBudget feature = new FeaturePerWorldTickBudget();
        ControlItemDto dto = serializer.toDto(feature);
        assertNotNull(dto.description);
        assertFalse(dto.description.isBlank());
    }

    @Test
    void featureCategory_isNonBlank() {
        ControlSerializer serializer = new ControlSerializer();
        FeaturePerWorldTickBudget feature = new FeaturePerWorldTickBudget();
        ControlItemDto dto = serializer.toDto(feature);
        assertNotNull(dto.category);
        assertFalse(dto.category.isBlank());
    }

    @Test
    void featureKnobs_containsBudgetMsAsDouble35() {
        ControlSerializer serializer = new ControlSerializer();
        FeaturePerWorldTickBudget feature = new FeaturePerWorldTickBudget();
        ControlItemDto dto = serializer.toDto(feature);
        KnobDto knob = findKnob(dto.knobs, "budgetMs");
        assertNotNull(knob, "knob budgetMs missing");
        assertEquals("double", knob.type);
        assertEquals(35.0, ((Number) knob.value).doubleValue(), 0.0001);
    }

    @Test
    void featureKnobs_containsPanicMs() {
        ControlSerializer serializer = new ControlSerializer();
        FeaturePerWorldTickBudget feature = new FeaturePerWorldTickBudget();
        ControlItemDto dto = serializer.toDto(feature);
        assertNotNull(findKnob(dto.knobs, "panicMs"), "knob panicMs missing");
    }

    @Test
    void featureKnobs_containsReleaseMs() {
        ControlSerializer serializer = new ControlSerializer();
        FeaturePerWorldTickBudget feature = new FeaturePerWorldTickBudget();
        ControlItemDto dto = serializer.toDto(feature);
        assertNotNull(findKnob(dto.knobs, "releaseMs"), "knob releaseMs missing");
    }

    @Test
    void featureKnobs_containsTickIntervalMsAsInt() {
        ControlSerializer serializer = new ControlSerializer();
        FeaturePerWorldTickBudget feature = new FeaturePerWorldTickBudget();
        ControlItemDto dto = serializer.toDto(feature);
        KnobDto knob = findKnob(dto.knobs, "tickIntervalMS");
        assertNotNull(knob, "knob tickIntervalMS missing");
        assertEquals("int", knob.type);
    }

    @Test
    void featureKnobs_noEnabledKnob() {
        ControlSerializer serializer = new ControlSerializer();
        FeaturePerWorldTickBudget feature = new FeaturePerWorldTickBudget();
        ControlItemDto dto = serializer.toDto(feature);
        assertNull(findKnob(dto.knobs, "enabled"), "enabled must not appear as a knob");
    }

    @Test
    void tweakId_isFastDrops() {
        ControlSerializer serializer = new ControlSerializer();
        TweakFastDrops tweak = new TweakFastDrops();
        ControlItemDto dto = serializer.toDto(tweak);
        assertEquals("fast-drops", dto.id);
    }

    @Test
    void tweakName_isFastDrops() {
        ControlSerializer serializer = new ControlSerializer();
        TweakFastDrops tweak = new TweakFastDrops();
        ControlItemDto dto = serializer.toDto(tweak);
        assertEquals("Fast Drops", dto.name);
    }

    @Test
    void tweakEnabled_isTrueByDefault() {
        ControlSerializer serializer = new ControlSerializer();
        TweakFastDrops tweak = new TweakFastDrops();
        ControlItemDto dto = serializer.toDto(tweak);
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
