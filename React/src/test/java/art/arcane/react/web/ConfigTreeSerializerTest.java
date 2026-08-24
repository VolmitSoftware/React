package art.arcane.react.web;

import art.arcane.react.api.web.ConfigTreeSerializer;
import art.arcane.react.api.web.dto.ConfigNodeDto;
import art.arcane.react.api.web.dto.ConfigSectionDto;
import art.arcane.react.model.ReactConfiguration;
import art.arcane.volmlib.util.localization.VolmitLocales;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfigTreeSerializerTest {

    private static List<ConfigNodeDto> flattenNodes(ConfigSectionDto[] sections) {
        List<ConfigNodeDto> all = new ArrayList<>();
        for (ConfigSectionDto section : sections) {
            if (section.nodes != null) {
                all.addAll(Arrays.asList(section.nodes));
            }
        }
        return all;
    }

    private static ConfigNodeDto findNode(List<ConfigNodeDto> nodes, String key) {
        for (ConfigNodeDto node : nodes) {
            if (key.equals(node.key)) {
                return node;
            }
        }
        return null;
    }

    @Test
    void it_emits_bool_nodes_for_top_level_scalar_fields() {
        ConfigTreeSerializer serializer = new ConfigTreeSerializer();
        ConfigSectionDto[] sections = serializer.serialize(new ReactConfiguration());
        List<ConfigNodeDto> all = flattenNodes(sections);

        ConfigNodeDto verbose = findNode(all, "main.verbose");
        assertNotNull(verbose, "Expected node with key main.verbose");
        assertEquals("bool", verbose.type);
        assertEquals(Boolean.FALSE, verbose.value);

        ConfigNodeDto debug = findNode(all, "main.debug");
        assertNotNull(debug, "Expected node with key main.debug");
        assertEquals("bool", debug.type);

        ConfigNodeDto customColors = findNode(all, "main.customColors");
        assertNotNull(customColors, "Expected node with key main.customColors");
        assertEquals("bool", customColors.type);
        assertEquals(Boolean.TRUE, customColors.value);
    }

    @Test
    void it_emits_enum_node_with_options_and_value() {
        ConfigTreeSerializer serializer = new ConfigTreeSerializer();
        ConfigSectionDto[] sections = serializer.serialize(new ReactConfiguration());
        List<ConfigNodeDto> all = flattenNodes(sections);

        ConfigNodeDto mode = findNode(all, "main.slowTickLogMode");
        assertNotNull(mode, "Expected node with key main.slowTickLogMode");
        assertEquals("enum", mode.type);
        assertEquals("BLAME", mode.value);
        assertNotNull(mode.options);
        List<String> opts = Arrays.asList(mode.options);
        assertTrue(opts.contains("OFF"), "options must contain OFF");
        assertTrue(opts.contains("BLAME"), "options must contain BLAME");
        assertTrue(opts.contains("SHORT"), "options must contain SHORT");
        assertTrue(opts.contains("DETAILED"), "options must contain DETAILED");
    }

    @Test
    void it_emits_main_language_as_supported_locale_dropdown() {
        ConfigTreeSerializer serializer = new ConfigTreeSerializer();
        ConfigSectionDto[] sections = serializer.serialize(new ReactConfiguration());
        ConfigNodeDto language = findNode(flattenNodes(sections), "main.language");

        assertNotNull(language, "Expected node with key main.language");
        assertEquals("enum", language.type);
        assertEquals("en_US", language.value);
        assertEquals(VolmitLocales.all(), Arrays.asList(language.options));
    }

    @Test
    void it_emits_nested_value_config_node_with_doc() {
        ConfigTreeSerializer serializer = new ConfigTreeSerializer();
        ConfigSectionDto[] sections = serializer.serialize(new ReactConfiguration());
        List<ConfigNodeDto> all = flattenNodes(sections);

        ConfigNodeDto baseValue = findNode(all, "main.value.baseValue");
        assertNotNull(baseValue, "Expected node with key main.value.baseValue");
        assertEquals("double", baseValue.type);

        Number numVal = (Number) baseValue.value;
        assertEquals(100.0, numVal.doubleValue(), 0.001);

        assertNotNull(baseValue.doc);
        assertFalse(baseValue.doc.isBlank(), "doc should be non-blank for annotated field");
    }

    @Test
    void it_does_not_emit_map_or_collection_as_scalar_nodes() {
        ConfigTreeSerializer serializer = new ConfigTreeSerializer();
        ConfigSectionDto[] sections = serializer.serialize(new ReactConfiguration());
        List<ConfigNodeDto> all = flattenNodes(sections);

        for (ConfigNodeDto node : all) {
            assertFalse(
                node.value instanceof Map,
                "Node " + node.key + " should not have a Map value"
            );
            assertFalse(
                node.value instanceof Collection,
                "Node " + node.key + " should not have a Collection value"
            );
        }

        assertNull(findNode(all, "main.value.valueMutlipliers"), "valueMutlipliers should not be a scalar node");
        assertNull(findNode(all, "main.monitoring.monitorConfiguration"), "monitorConfiguration should not be a scalar node");
    }

    @Test
    void it_prefixes_all_keys_with_main_and_options_doc_never_null() {
        ConfigTreeSerializer serializer = new ConfigTreeSerializer();
        ConfigSectionDto[] sections = serializer.serialize(new ReactConfiguration());
        List<ConfigNodeDto> all = flattenNodes(sections);

        assertFalse(all.isEmpty(), "Expected at least one node");
        for (ConfigNodeDto node : all) {
            assertTrue(
                node.key.startsWith("main."),
                "Key " + node.key + " must start with main."
            );
            assertNotNull(node.doc, "doc must not be null for key " + node.key);
            assertNotNull(node.options, "options must not be null for key " + node.key);
        }
    }
}
