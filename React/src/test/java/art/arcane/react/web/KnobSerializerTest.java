package art.arcane.react.web;

import art.arcane.react.api.web.KnobSerializer;
import art.arcane.react.api.web.dto.KnobDto;
import art.arcane.react.util.project.config.ConfigDoc;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class KnobSerializerTest {

    private enum SomeEnum { A, B, C }

    private static final class Fixture {
        @ConfigDoc("d-int") private int count = 5;
        @ConfigDoc("d-double") private double budgetMs = 35.0;
        @ConfigDoc("d-bool") private boolean fast = true;
        @ConfigDoc("d-str") private String label = "hi";
        @ConfigDoc("d-enum") private SomeEnum mode = SomeEnum.B;
        private int hidden = 1;
        private transient int t = 2;
        @ConfigDoc("enable flag") private boolean enabled = true;
    }

    @Test
    void it_returns_exactly_5_knobs_excluding_hidden_transient_and_enabled() {
        KnobSerializer serializer = new KnobSerializer();
        Fixture fixture = new Fixture();
        List<KnobDto> knobs = serializer.knobs(fixture);
        assertEquals(5, knobs.size());
    }

    @Test
    void it_maps_types_correctly() {
        KnobSerializer serializer = new KnobSerializer();
        Fixture fixture = new Fixture();
        List<KnobDto> knobs = serializer.knobs(fixture);
        KnobDto count = knobByKey(knobs, "count");
        KnobDto budgetMs = knobByKey(knobs, "budgetMs");
        KnobDto fast = knobByKey(knobs, "fast");
        KnobDto labelKnob = knobByKey(knobs, "label");
        KnobDto mode = knobByKey(knobs, "mode");
        assertEquals("int", count.type);
        assertEquals("double", budgetMs.type);
        assertEquals("bool", fast.type);
        assertEquals("string", labelKnob.type);
        assertEquals("enum", mode.type);
    }

    @Test
    void it_sets_enum_options_and_value_name() {
        KnobSerializer serializer = new KnobSerializer();
        Fixture fixture = new Fixture();
        List<KnobDto> knobs = serializer.knobs(fixture);
        KnobDto mode = knobByKey(knobs, "mode");
        assertNotNull(mode.options);
        assertEquals(3, mode.options.length);
        assertEquals("A", mode.options[0]);
        assertEquals("B", mode.options[1]);
        assertEquals("C", mode.options[2]);
        assertEquals("B", mode.value);
    }

    @Test
    void it_sets_int_value_as_boxed_integer() {
        KnobSerializer serializer = new KnobSerializer();
        Fixture fixture = new Fixture();
        List<KnobDto> knobs = serializer.knobs(fixture);
        KnobDto count = knobByKey(knobs, "count");
        assertEquals(Integer.valueOf(5), count.value);
    }

    @Test
    void it_sets_doc_from_annotation_value() {
        KnobSerializer serializer = new KnobSerializer();
        Fixture fixture = new Fixture();
        List<KnobDto> knobs = serializer.knobs(fixture);
        assertEquals("d-int", knobByKey(knobs, "count").doc);
        assertEquals("d-double", knobByKey(knobs, "budgetMs").doc);
        assertEquals("d-bool", knobByKey(knobs, "fast").doc);
        assertEquals("d-str", knobByKey(knobs, "label").doc);
        assertEquals("d-enum", knobByKey(knobs, "mode").doc);
    }

    @Test
    void it_humanizes_budget_ms_label() {
        KnobSerializer serializer = new KnobSerializer();
        Fixture fixture = new Fixture();
        List<KnobDto> knobs = serializer.knobs(fixture);
        KnobDto budgetMs = knobByKey(knobs, "budgetMs");
        assertEquals("Budget Ms", budgetMs.label);
    }

    @Test
    void it_returns_empty_list_for_null_configurable() {
        KnobSerializer serializer = new KnobSerializer();
        List<KnobDto> knobs = serializer.knobs(null);
        assertNotNull(knobs);
        assertEquals(0, knobs.size());
    }

    private static KnobDto knobByKey(List<KnobDto> knobs, String key) {
        for (KnobDto knob : knobs) {
            if (key.equals(knob.key)) {
                return knob;
            }
        }
        throw new AssertionError("No knob with key: " + key);
    }
}
