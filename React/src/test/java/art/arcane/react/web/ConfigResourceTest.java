package art.arcane.react.web;

import art.arcane.react.api.web.ConfigApplier;
import art.arcane.react.api.web.PresetApplier;
import art.arcane.react.api.web.pairing.WebToken;
import art.arcane.react.api.web.WebMutation;
import art.arcane.react.api.web.dto.ConfigSectionDto;
import art.arcane.react.api.web.dto.ConfigNodeDto;
import art.arcane.react.api.web.dto.Envelope;
import art.arcane.react.api.web.resource.ConfigResource;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import art.arcane.react.api.web.WebError;
import io.javalin.http.InternalServerErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ConfigResourceTest {

    private WebToken adminToken;
    private WebToken opToken;
    private WebToken readToken;
    private RecordingConfigApplier recordingApplier;
    private RecordingPresetApplier recordingPresetApplier;
    private List<WebMutation> mutations;
    private ConfigResource resource;

    @BeforeEach
    void setUp() {



        adminToken = WebTestTokens.token("tok-cfg-admin", "admin");
        opToken = WebTestTokens.token("tok-cfg-op", "operator");
        readToken = WebTestTokens.token("tok-cfg-read", "viewer");

        recordingApplier = new RecordingConfigApplier();
        recordingPresetApplier = new RecordingPresetApplier();
        mutations = new ArrayList<>();
        resource = new ConfigResource(
            this::buildFakeTree,
            recordingApplier,
            recordingPresetApplier,
            (context, mutation) -> mutations.add(mutation)
        );
    }

    private ConfigSectionDto[] buildFakeTree() {
        ConfigSectionDto section = new ConfigSectionDto();
        section.name = "general";
        ConfigNodeDto node = new ConfigNodeDto();
        node.key = "main.verbose";
        node.label = "Verbose";
        node.type = "bool";
        node.value = Boolean.FALSE;
        node.options = new String[0];
        node.doc = "";
        ConfigNodeDto debug = new ConfigNodeDto();
        debug.key = "main.debug";
        debug.label = "Debug";
        debug.type = "bool";
        debug.value = Boolean.FALSE;
        debug.options = new String[0];
        debug.doc = "";
        section.nodes = new ConfigNodeDto[]{node, debug};
        return new ConfigSectionDto[]{section};
    }

    @Test
    void get_returns_envelope_with_sections_length_one() {
        Context ctx = mock(Context.class);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        resource.get(ctx);
        verify(ctx).json(captor.capture());

        @SuppressWarnings("unchecked")
        Envelope<ConfigResource.ConfigData> envelope = (Envelope<ConfigResource.ConfigData>) captor.getValue();
        assertNotNull(envelope.data(), "envelope.data must not be null");
        assertNotNull(envelope.data().sections(), "envelope.data.sections must not be null");
        assertEquals(1, envelope.data().sections().length, "Expected 1 section from fake tree supplier");
    }

    @Test
    void put_with_admin_token_applies_values_and_returns_envelope() {
        Context ctx = mock(Context.class);
        when(ctx.<WebToken>attribute("token")).thenReturn(adminToken);
        when(ctx.bodyAsClass(Map.class)).thenReturn(Map.of("main.verbose", Boolean.TRUE));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        resource.put(ctx);
        verify(ctx).json(captor.capture());

        assertTrue(recordingApplier.invocations.contains("main.verbose"), "Applier should have been called with main.verbose");
        @SuppressWarnings("unchecked")
        Envelope<ConfigResource.ConfigData> envelope = (Envelope<ConfigResource.ConfigData>) captor.getValue();
        assertNotNull(envelope.data().sections());
        assertEquals(1, mutations.size());
        assertEquals("config.update", mutations.get(0).operation());
    }

    @Test
    void put_with_op_token_throws_forbidden_and_applier_not_invoked() {
        Context ctx = mock(Context.class);
        when(ctx.<WebToken>attribute("token")).thenReturn(opToken);
        when(ctx.bodyAsClass(Map.class)).thenReturn(Map.of("main.verbose", Boolean.TRUE));

        assertThrows(WebError.class, () -> resource.put(ctx));
        assertTrue(recordingApplier.invocations.isEmpty(), "Applier must not be invoked when op token is used for global config write");
    }

    @Test
    void put_with_empty_map_throws_bad_request() {
        Context ctx = mock(Context.class);
        when(ctx.<WebToken>attribute("token")).thenReturn(adminToken);
        when(ctx.bodyAsClass(Map.class)).thenReturn(Map.of());

        assertThrows(BadRequestResponse.class, () -> resource.put(ctx));
        assertTrue(recordingApplier.invocations.isEmpty(), "Applier must not be invoked on empty body");
    }

    @Test
    void put_rolls_back_prior_values_and_does_not_report_when_a_later_value_fails() {
        Context ctx = mock(Context.class);
        when(ctx.<WebToken>attribute("token")).thenReturn(adminToken);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("main.verbose", Boolean.TRUE);
        body.put("main.debug", Boolean.TRUE);
        when(ctx.bodyAsClass(Map.class)).thenReturn(body);
        List<String> calls = new ArrayList<>();
        ConfigResource rejecting = new ConfigResource(
            this::buildFakeTree,
            (path, value) -> {
                calls.add(path + "=" + value);
                return !"main.debug".equals(path);
            },
            recordingPresetApplier,
            (context, mutation) -> mutations.add(mutation)
        );

        assertThrows(InternalServerErrorResponse.class, () -> rejecting.put(ctx));
        assertEquals(List.of(
            "main.verbose=true",
            "main.debug=true",
            "main.verbose=false",
            "main.debug=false"
        ), calls);
        assertTrue(mutations.isEmpty());
    }

    @Test
    void put_restores_the_failed_path_when_its_unconfirmed_write_lands_later() {
        Context ctx = mock(Context.class);
        when(ctx.<WebToken>attribute("token")).thenReturn(adminToken);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("main.verbose", Boolean.TRUE);
        body.put("main.debug", Boolean.TRUE);
        when(ctx.bodyAsClass(Map.class)).thenReturn(body);
        Map<String, Object> live = new LinkedHashMap<>();
        live.put("main.verbose", Boolean.FALSE);
        live.put("main.debug", Boolean.FALSE);
        List<String> timedOut = new ArrayList<>();
        ConfigResource timingOut = new ConfigResource(
            this::buildFakeTree,
            (path, value) -> {
                live.put(path, value);
                if ("main.debug".equals(path) && timedOut.isEmpty()) {
                    timedOut.add(path);
                    return false;
                }
                return true;
            },
            recordingPresetApplier,
            (context, mutation) -> mutations.add(mutation)
        );

        InternalServerErrorResponse error = assertThrows(InternalServerErrorResponse.class, () -> timingOut.put(ctx));

        assertEquals("Failed to apply config path: main.debug", error.getMessage());
        assertEquals(Boolean.FALSE, live.get("main.verbose"));
        assertEquals(Boolean.FALSE, live.get("main.debug"));
        assertTrue(mutations.isEmpty());
    }

    @Test
    void put_with_read_only_token_throws_forbidden_and_applier_not_invoked() {
        Context ctx = mock(Context.class);
        when(ctx.<WebToken>attribute("token")).thenReturn(readToken);
        when(ctx.bodyAsClass(Map.class)).thenReturn(Map.of("main.verbose", Boolean.TRUE));

        assertThrows(WebError.class, () -> resource.put(ctx));
        assertTrue(recordingApplier.invocations.isEmpty(), "Applier must not be invoked when scope check fails");
    }

    @Test
    void preset_with_admin_token_and_known_name_invokes_preset_applier_and_returns_envelope() {
        Context ctx = mock(Context.class);
        when(ctx.<WebToken>attribute("token")).thenReturn(adminToken);
        when(ctx.pathParam("name")).thenReturn("balanced");

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        resource.preset(ctx);
        verify(ctx).json(captor.capture());

        assertTrue(recordingPresetApplier.invocations.contains("balanced"), "PresetApplier should have been called with balanced");
        @SuppressWarnings("unchecked")
        Envelope<ConfigResource.ConfigData> envelope = (Envelope<ConfigResource.ConfigData>) captor.getValue();
        assertNotNull(envelope.data().sections());
    }

    @Test
    void preset_with_op_token_and_known_name_throws_forbidden() {
        Context ctx = mock(Context.class);
        when(ctx.<WebToken>attribute("token")).thenReturn(opToken);
        when(ctx.pathParam("name")).thenReturn("balanced");

        assertThrows(WebError.class, () -> resource.preset(ctx));
        assertTrue(recordingPresetApplier.invocations.isEmpty(), "PresetApplier must not be invoked when op token is used for global preset");
    }

    @Test
    void preset_with_admin_token_and_unknown_name_throws_bad_request_before_applier() {
        Context ctx = mock(Context.class);
        when(ctx.<WebToken>attribute("token")).thenReturn(adminToken);
        when(ctx.pathParam("name")).thenReturn("bogus");

        assertThrows(BadRequestResponse.class, () -> resource.preset(ctx));
        assertTrue(recordingPresetApplier.invocations.isEmpty(), "PresetApplier must NOT be invoked for unknown preset name");
    }

    @Test
    void preset_with_read_only_token_and_known_name_throws_forbidden() {
        Context ctx = mock(Context.class);
        when(ctx.<WebToken>attribute("token")).thenReturn(readToken);
        when(ctx.pathParam("name")).thenReturn("balanced");

        assertThrows(WebError.class, () -> resource.preset(ctx));
        assertTrue(recordingPresetApplier.invocations.isEmpty(), "PresetApplier must NOT be invoked when scope check fails");
    }

    private static final class RecordingConfigApplier implements ConfigApplier {
        final List<String> invocations = new ArrayList<>();

        @Override
        public boolean apply(String path, Object value) {
            invocations.add(path);
            return true;
        }
    }

    private static final class RecordingPresetApplier implements PresetApplier {
        final List<String> invocations = new ArrayList<>();

        @Override
        public int apply(String name) {
            invocations.add(name);
            if ("off".equals(name) || "light".equals(name) || "balanced".equals(name) || "high".equals(name)) {
                return 3;
            }
            return -1;
        }
    }
}
