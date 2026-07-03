package art.arcane.react.web;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.web.WebConfiguration;
import art.arcane.react.api.web.dto.IdentityDto;
import art.arcane.react.api.web.relay.RelayClient;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.core.controller.WebController;
import art.arcane.react.util.project.registry.Registry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class WebControllerRelayTest {

    private WebController controller;

    private WebController buildController(WebConfiguration config, File dataFolder, SampleController sampleController) {
        WebController c = new WebController() {
            @Override
            protected void executeAsync(Runnable r) {
                Thread.ofVirtual().start(r);
            }

            @Override
            protected IdentityDto resolveIdentity() {
                IdentityDto dto = new IdentityDto();
                dto.version = "9.9.9-relay-test";
                dto.serverName = "RelayTestBrand";
                dto.folia = false;
                dto.serverId = "127.0.0.1:0";
                return dto;
            }
        };
        c.setConfig(config);
        c.setDataFolder(dataFolder);
        c.setSampleController(sampleController);
        return c;
    }

    @AfterEach
    void cleanup() {
        if (controller != null) {
            controller.stop();
            controller = null;
        }
    }

    @Test
    void relayDisabledStartsNoClient(@TempDir File dataFolder) throws Exception {
        SampleController sampleController = mock(SampleController.class);
        @SuppressWarnings("unchecked")
        Registry<Sampler> registry = mock(Registry.class);
        when(sampleController.getSamplers()).thenReturn(registry);
        when(registry.all()).thenReturn(List.of());

        WebConfiguration config = new WebConfiguration();
        config.setEnabled(true);
        config.setBindAddress("127.0.0.1");
        config.setPort(0);
        config.setRelayEnabled(false);

        controller = buildController(config, dataFolder, sampleController);
        controller.start();
        controller.postStart();
        controller.awaitStart(5000);

        assertNotNull(controller.getIdentity(), "Identity must be loaded even when relay is disabled");
        assertNull(controller.getRelayClient(), "RelayClient must be null when relayEnabled=false");
    }

    @Test
    void relayEnabledWithUnreachableUrlStartsClientWithoutCrashing(@TempDir File dataFolder) throws Exception {
        SampleController sampleController = mock(SampleController.class);
        @SuppressWarnings("unchecked")
        Registry<Sampler> registry = mock(Registry.class);
        when(sampleController.getSamplers()).thenReturn(registry);
        when(registry.all()).thenReturn(List.of());

        WebConfiguration config = new WebConfiguration();
        config.setEnabled(true);
        config.setBindAddress("127.0.0.1");
        config.setPort(0);
        config.setRelayEnabled(true);
        config.setRelayUrl("wss://127.0.0.1:1");

        controller = buildController(config, dataFolder, sampleController);
        controller.start();
        controller.postStart();
        controller.awaitStart(5000);

        assertNotNull(controller.getIdentity(), "Identity must be loaded when relay is enabled");
        RelayClient rc = controller.getRelayClient();
        assertNotNull(rc, "RelayClient must be non-null when relayEnabled=true and relayUrl is set");

        controller.stop();
        assertNull(controller.getRelayClient(), "RelayClient must be null after stop()");
        controller = null;
    }
}
