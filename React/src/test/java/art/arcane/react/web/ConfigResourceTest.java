package art.arcane.react.web;

import art.arcane.react.api.web.ConfigApplier;
import art.arcane.react.api.web.PresetApplier;
import art.arcane.react.api.web.PairingToken;
import art.arcane.react.api.web.TokenRecord;
import art.arcane.react.api.web.TokenStore;
import art.arcane.react.api.web.WebMutation;
import art.arcane.react.api.web.dto.ConfigSectionDto;
import art.arcane.react.api.web.dto.ConfigNodeDto;
import art.arcane.react.api.web.dto.Envelope;
import art.arcane.react.api.web.resource.ConfigResource;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.InternalServerErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.SecureRandom;
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

    private PairingToken adminToken;
    private PairingToken opToken;
    private PairingToken readToken;
    private RecordingConfigApplier recordingApplier;
    private RecordingPresetApplier recordingPresetApplier;
    private List<WebMutation> mutations;
    private ConfigResource resource;

    @BeforeEach
    void setUp() {
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);

        TokenRecord adminRecord = new TokenRecord("tok-cfg-admin", "admin-device", 1000L, Set.of("read", "op:execute", "admin"), "admin");
        TokenRecord opRecord = new TokenRecord("tok-cfg-op", "op-device", 1000L, Set.of("read", "op:execute"), "operator");
        TokenRecord readRecord = new TokenRecord("tok-cfg-read", "read-device", 1000L, Set.of("read"), "viewer");
        TokenStore store = TokenStore.inMemory(adminRecord, opRecord, readRecord);

        String adminBearer = PairingToken.mint(secret, adminRecord.id(), adminRecord.label(), adminRecord.issuedAt(), adminRecord.scopes());
        String opBearer = PairingToken.mint(secret, opRecord.id(), opRecord.label(), opRecord.issuedAt(), opRecord.scopes());
        String readBearer = PairingToken.mint(secret, readRecord.id(), readRecord.label(), readRecord.issuedAt(), readRecord.scopes());

        adminToken = PairingToken.verify(secret, adminBearer, store).orElseThrow();
        opToken = PairingToken.verify(secret, opBearer, store).orElseThrow();
        readToken = PairingToken.verify(secret, readBearer, store).orElseThrow();

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
        when(ctx.<PairingToken>attribute("token")).thenReturn(adminToken);
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
        when(ctx.<PairingToken>attribute("token")).thenReturn(opToken);
        when(ctx.bodyAsClass(Map.class)).thenReturn(Map.of("main.verbose", Boolean.TRUE));

        assertThrows(ForbiddenResponse.class, () -> resource.put(ctx));
        assertTrue(recordingApplier.invocations.isEmpty(), "Applier must not be invoked when op token is used for global config write");
    }

    @Test
    void put_with_empty_map_throws_bad_request() {
        Context ctx = mock(Context.class);
        when(ctx.<PairingToken>attribute("token")).thenReturn(adminToken);
        when(ctx.bodyAsClass(Map.class)).thenReturn(Map.of());

        assertThrows(BadRequestResponse.class, () -> resource.put(ctx));
        assertTrue(recordingApplier.invocations.isEmpty(), "Applier must not be invoked on empty body");
    }

    @Test
    void put_rolls_back_prior_values_and_does_not_report_when_a_later_value_fails() {
        Context ctx = mock(Context.class);
        when(ctx.<PairingToken>attribute("token")).thenReturn(adminToken);
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
            "main.verbose=false"
        ), calls);
        assertTrue(mutations.isEmpty());
    }

    @Test
    void put_with_read_only_token_throws_forbidden_and_applier_not_invoked() {
        Context ctx = mock(Context.class);
        when(ctx.<PairingToken>attribute("token")).thenReturn(readToken);
        when(ctx.bodyAsClass(Map.class)).thenReturn(Map.of("main.verbose", Boolean.TRUE));

        assertThrows(ForbiddenResponse.class, () -> resource.put(ctx));
        assertTrue(recordingApplier.invocations.isEmpty(), "Applier must not be invoked when scope check fails");
    }

    @Test
    void preset_with_admin_token_and_known_name_invokes_preset_applier_and_returns_envelope() {
        Context ctx = mock(Context.class);
        when(ctx.<PairingToken>attribute("token")).thenReturn(adminToken);
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
        when(ctx.<PairingToken>attribute("token")).thenReturn(opToken);
        when(ctx.pathParam("name")).thenReturn("balanced");

        assertThrows(ForbiddenResponse.class, () -> resource.preset(ctx));
        assertTrue(recordingPresetApplier.invocations.isEmpty(), "PresetApplier must not be invoked when op token is used for global preset");
    }

    @Test
    void preset_with_admin_token_and_unknown_name_throws_bad_request_before_applier() {
        Context ctx = mock(Context.class);
        when(ctx.<PairingToken>attribute("token")).thenReturn(adminToken);
        when(ctx.pathParam("name")).thenReturn("bogus");

        assertThrows(BadRequestResponse.class, () -> resource.preset(ctx));
        assertTrue(recordingPresetApplier.invocations.isEmpty(), "PresetApplier must NOT be invoked for unknown preset name");
    }

    @Test
    void preset_with_read_only_token_and_known_name_throws_forbidden() {
        Context ctx = mock(Context.class);
        when(ctx.<PairingToken>attribute("token")).thenReturn(readToken);
        when(ctx.pathParam("name")).thenReturn("balanced");

        assertThrows(ForbiddenResponse.class, () -> resource.preset(ctx));
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
