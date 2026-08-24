package art.arcane.react.web;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.web.WebConfiguration;
import art.arcane.react.api.web.dto.IdentityDto;
import art.arcane.react.api.web.relay.RelayClient;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.core.controller.WebController;
import art.arcane.react.util.project.registry.Registry;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    private TestWebController buildInspectableController(WebConfiguration config) {
        TestWebController c = new TestWebController();
        c.setConfig(config);
        c.setApp(Javalin.create());
        c.setBoundPort(config.getPort());
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
        config.setListenerEnabled(true);
        config.setListenAddress("127.0.0.1");
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
        config.setListenerEnabled(true);
        config.setListenAddress("127.0.0.1");
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

    @Test
    void relayLoopbackAlwaysUsesHttpEvenWhenAdvertisedUrlIsHttps() {
        WebConfiguration config = new WebConfiguration();
        config.setAdvertisedUrl("https://react.example.net/direct");
        TestWebController c = buildInspectableController(config);

        assertEquals("http://127.0.0.1:41234", c.loopbackUrl(41234));
    }

    @Test
    void advertisedUrlBecomesNormalizedDirectUrl() {
        WebConfiguration config = new WebConfiguration();
        config.setAdvertisedUrl("  https://react.example.net/proxy/react/  ");
        TestWebController c = buildInspectableController(config);

        assertEquals("https://react.example.net/proxy/react", c.resolveDirectUrl());
    }

    @Test
    void blankAdvertisedUrlUsesHttpListenerFallback() {
        WebConfiguration config = new WebConfiguration();
        config.setListenAddress("0.0.0.0");
        config.setPort(9697);
        TestWebController c = buildInspectableController(config);

        assertEquals("http://127.0.0.1:9697", c.resolveDirectUrl());
    }

    @Test
    void invalidAdvertisedUrlIsRejected() {
        WebConfiguration config = new WebConfiguration();
        config.setAdvertisedUrl("ftp://react.example.net");
        TestWebController c = buildInspectableController(config);

        assertThrows(IllegalArgumentException.class, c::resolveDirectUrl);
    }

    @Test
    void unboundListenerCannotProduceDirectUrl() {
        WebConfiguration config = new WebConfiguration();
        TestWebController c = new TestWebController();
        c.setConfig(config);

        assertThrows(IllegalStateException.class, c::resolveDirectUrl);
    }

    private static final class TestWebController extends WebController {

        String loopbackUrl(int port) {
            return resolveRelayLoopbackUrl(port);
        }
    }
}
