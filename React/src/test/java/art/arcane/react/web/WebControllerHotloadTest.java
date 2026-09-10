package art.arcane.react.web;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.web.PairingToken;
import art.arcane.react.api.web.TokenRecord;
import art.arcane.react.api.web.TokenStore;
import art.arcane.react.api.web.WebConfiguration;
import art.arcane.react.api.web.dto.IdentityDto;
import art.arcane.react.api.web.relay.ReactServerIdentity;
import art.arcane.react.api.web.relay.RelayClient;
import art.arcane.react.api.web.relay.RelayLoopbackBridge;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.core.controller.WebController;
import art.arcane.react.util.project.registry.Registry;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

class WebControllerHotloadTest {
    @TempDir
    private File dataFolder;
    private TestWebController controller;

    @AfterEach
    void tearDown() {
        if (controller != null) {
            controller.releaseBind.countDown();
            controller.stop();
        }
    }

    @Test
    void preparationUsesCapturedBytesWithoutRewritingTheFile() throws Exception {
        File source = new File(dataFolder, "web.toml");
        String disk = "listenerEnabled = false\nlistenAddress = \"::\"\n";
        Files.writeString(source.toPath(), disk, StandardCharsets.UTF_8);

        WebConfiguration prepared = WebController.prepareHotloadSnapshot(source,
            "listenerEnabled = true\nlistenAddress = \"127.0.0.1\"\nport = 0\n"
                + "corsOrigins = [\"*\", \"null\"]\nwsPushHz = 20\n");

        Assertions.assertTrue(prepared.isListenerEnabled());
        Assertions.assertEquals("127.0.0.1", prepared.getListenAddress());
        Assertions.assertEquals(0, prepared.getPort());
        Assertions.assertEquals(List.of("*", "null"), prepared.getCorsOrigins());
        Assertions.assertEquals(20, prepared.getWsPushHz());
        Assertions.assertTrue(prepared.isRequireTokenForReads());
        Assertions.assertEquals(disk, Files.readString(source.toPath(), StandardCharsets.UTF_8));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "listenerEnabled = true\n", "listenAddress = \"::\"\n",
        "listenerEnabled = true\nlistenAddress = [\n",
        "listenerEnabled = true\nlistenAddress = \"\"\n",
        "listenerEnabled = true\nlistenAddress = \"::\"\nport = -1\n",
        "listenerEnabled = true\nlistenAddress = \"::\"\nport = 65536\n",
        "listenerEnabled = true\nlistenAddress = \"::\"\nwsPushHz = 0\n",
        "listenerEnabled = true\nlistenAddress = \"::\"\nwsPushHz = 1001\n",
        "listenerEnabled = true\nlistenAddress = \"::\"\nadvertisedUrl = \"ftp://example.com\"\n",
        "listenerEnabled = true\nlistenAddress = \"::\"\nrelayEnabled = true\nrelayUrl = \"http://example.com\"\n"
    })
    void preparationRejectsInvalidCurrentConfiguration(String raw) {
        Assertions.assertThrows(IOException.class,
            () -> WebController.prepareHotloadSnapshot(new File(dataFolder, "web.toml"), raw));
    }

    @Test
    void preparationRejectsOversizedInput() {
        String raw = "listenerEnabled = true\nlistenAddress = \"::\"\n#" + "x".repeat(2 * 1024 * 1024);
        Assertions.assertThrows(IOException.class,
            () -> WebController.prepareHotloadSnapshot(new File(dataFolder, "web.toml"), raw));
    }

    @Test
    void reloadRebindsTheSamePortAndAppliesReadPolicyCorsAndAdvertisedUrl() throws Exception {
        startController();
        int port = controller.getBoundPort();
        Javalin previousApp = controller.getApp();
        ExecutorService previousSendExecutor = controller.getWsSendExecutor();
        ScheduledExecutorService previousPushExecutor = controller.getWsPushExecutor();
        Assertions.assertEquals(401, get("/api/v1/identity", null, null).statusCode());

        WebConfiguration updated = configuration();
        updated.setPort(port);
        updated.setRequireTokenForReads(false);
        updated.setWsPushHz(20);
        updated.setCorsOrigins(List.of("https://dashboard.example"));
        updated.setAdvertisedUrl("https://dashboard.example/react");

        Assertions.assertTrue(controller.applyHotloadSnapshot(updated));

        Assertions.assertEquals(port, controller.getBoundPort());
        Assertions.assertNotSame(previousApp, controller.getApp());
        Assertions.assertTrue(previousSendExecutor.isShutdown());
        Assertions.assertTrue(previousPushExecutor.isShutdown());
        Assertions.assertEquals(20, controller.getConfig().getWsPushHz());
        Assertions.assertEquals("https://dashboard.example/react", controller.resolveDirectUrl());
        HttpResponse<String> response = get("/api/v1/identity", null, "https://dashboard.example");
        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertEquals("https://dashboard.example", response.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
        Assertions.assertEquals(1, controller.logger.getHandlers().length);
    }

    @Test
    void reloadPreservesTokensSecretIdentityAndPersistedAuthBytes() throws Exception {
        startController();
        TokenStore tokens = controller.getTokenStore();
        TokenRecord record = new TokenRecord("hotload-token", "device", 1000L, Set.of("read"), "viewer");
        tokens.add(record);
        tokens.save(controller.tokensFile());
        byte[] persisted = Files.readAllBytes(controller.tokensFile().toPath());
        byte[] secret = controller.getSecret();
        ReactServerIdentity identity = controller.getIdentity();
        String bearer = PairingToken.mint(secret, record.id(), record.label(), record.issuedAt(), record.scopes());
        Assertions.assertEquals(200, get("/api/v1/identity", bearer, null).statusCode());

        WebConfiguration updated = configuration();
        updated.setWsPushHz(10);
        Assertions.assertTrue(controller.applyHotloadSnapshot(updated));

        Assertions.assertSame(tokens, controller.getTokenStore());
        Assertions.assertSame(secret, controller.getSecret());
        Assertions.assertSame(identity, controller.getIdentity());
        Assertions.assertArrayEquals(persisted, Files.readAllBytes(controller.tokensFile().toPath()));
        Assertions.assertEquals(200, get("/api/v1/identity", bearer, null).statusCode());
        Assertions.assertEquals(401, get("/api/v1/identity", null, null).statusCode());
    }

    @Test
    void disabledListenerCanBeEnabledAgainWithoutDiscardingAuth() throws Exception {
        startController();
        TokenStore tokens = controller.getTokenStore();
        WebConfiguration disabled = configuration();
        disabled.setListenerEnabled(false);

        Assertions.assertTrue(controller.applyHotloadSnapshot(disabled));
        Assertions.assertNull(controller.getApp());
        Assertions.assertEquals(0, controller.getBoundPort());
        Assertions.assertNull(controller.getWsPushExecutor());
        Assertions.assertFalse(controller.getConfig().isListenerEnabled());
        Assertions.assertSame(tokens, controller.getTokenStore());
        Assertions.assertTrue(controller.applyHotloadSnapshot(configuration()));
        Assertions.assertSame(tokens, controller.getTokenStore());
        Assertions.assertEquals(200, get("/api/v1/ping", null, null).statusCode());
    }

    @Test
    void relayChangesReplaceTheClientAndDisablingClosesIt() throws Exception {
        startController();
        WebConfiguration relayed = configuration();
        relayed.setRelayEnabled(true);
        relayed.setRelayUrl("wss://127.0.0.1:1");
        Assertions.assertTrue(controller.applyHotloadSnapshot(relayed));
        RelayClient previousRelay = controller.getRelayClient();
        Assertions.assertNotNull(previousRelay);

        relayed.setRelayUrl("wss://127.0.0.1:2");
        Assertions.assertTrue(controller.applyHotloadSnapshot(relayed));
        Assertions.assertNotSame(previousRelay, controller.getRelayClient());
        Assertions.assertTrue(controller.applyHotloadSnapshot(configuration()));
        Assertions.assertNull(controller.getRelayClient());
        Assertions.assertEquals(200, get("/api/v1/ping", null, null).statusCode());
    }

    @Test
    void bindFailureRestoresThePreviousListenerAndReadPolicy() throws Exception {
        startController();
        WebConfiguration previous = controller.getConfig();
        try (ServerSocket occupied = new ServerSocket()) {
            occupied.bind(new InetSocketAddress("127.0.0.1", 0));
            WebConfiguration updated = configuration();
            updated.setPort(occupied.getLocalPort());
            updated.setRequireTokenForReads(false);

            Assertions.assertFalse(controller.applyHotloadSnapshot(updated));

            Assertions.assertEquals(previous, controller.getConfig());
            Assertions.assertNull(controller.getStartFailure());
            Assertions.assertEquals(200, get("/api/v1/ping", null, null).statusCode());
            Assertions.assertEquals(401, get("/api/v1/identity", null, null).statusCode());
            Assertions.assertEquals(1, controller.logger.getHandlers().length);
        }
    }

    @Test
    void relayStartupFailureRestoresThePreviousListener() throws Exception {
        startController();
        WebConfiguration previous = controller.getConfig();
        controller.failRelayBridge.set(true);
        WebConfiguration updated = configuration();
        updated.setRelayEnabled(true);
        updated.setRelayUrl("wss://127.0.0.1:1");

        Assertions.assertFalse(controller.applyHotloadSnapshot(updated));

        Assertions.assertEquals(previous, controller.getConfig());
        Assertions.assertNull(controller.getRelayClient());
        Assertions.assertNull(controller.getStartFailure());
        Assertions.assertEquals(200, get("/api/v1/ping", null, null).statusCode());
        Assertions.assertEquals(1, controller.logger.getHandlers().length);
    }

    @Test
    void equalSnapshotKeepsTheCurrentListener() throws Exception {
        startController();
        Javalin previous = controller.getApp();

        Assertions.assertTrue(controller.applyHotloadSnapshot(configuration()));

        Assertions.assertSame(previous, controller.getApp());
    }

    @Test
    void stoppingDuringHotloadPreventsPublicationAndRollback() throws Exception {
        startController();
        controller.blockNextBind.set(true);
        WebConfiguration updated = configuration();
        updated.setWsPushHz(20);
        CompletableFuture<Boolean> application = CompletableFuture.supplyAsync(() -> controller.applyHotloadSnapshot(updated));
        try {
            Assertions.assertTrue(controller.awaitBind.await(5, TimeUnit.SECONDS));
            controller.stop();
        } finally {
            controller.releaseBind.countDown();
        }

        Assertions.assertFalse(application.get(5, TimeUnit.SECONDS));
        Assertions.assertNull(controller.getApp());
        Assertions.assertNull(controller.getWsPushExecutor());
        Assertions.assertEquals(0, controller.logger.getHandlers().length);
        Assertions.assertFalse(controller.applyHotloadSnapshot(configuration()));
    }

    @Test
    void hotloadSupersedesAnUnfinishedInitialStartup() throws Exception {
        createController();
        controller.blockNextBind.set(true);
        controller.start();
        controller.postStart();
        Assertions.assertTrue(controller.awaitBind.await(5, TimeUnit.SECONDS));
        WebConfiguration updated = configuration();
        updated.setRequireTokenForReads(false);
        try {
            Assertions.assertTrue(controller.applyHotloadSnapshot(updated));
        } finally {
            controller.releaseBind.countDown();
        }
        Assertions.assertTrue(controller.initialStartupFinished.await(5, TimeUnit.SECONDS));

        Assertions.assertEquals(updated, controller.getConfig());
        Assertions.assertEquals(200, get("/api/v1/identity", null, null).statusCode());
        Assertions.assertEquals(1, controller.logger.getHandlers().length);
    }

    private void startController() throws Exception {
        createController();
        controller.start();
        controller.postStart();
        controller.awaitStart(5000L);
    }

    @SuppressWarnings("unchecked")
    private void createController() {
        controller = new TestWebController();
        controller.setDataFolder(dataFolder);
        controller.setConfig(configuration());
        SampleController samples = Mockito.mock(SampleController.class);
        Registry<Sampler> samplers = Mockito.mock(Registry.class);
        Mockito.when(samples.getSamplers()).thenReturn(samplers);
        Mockito.when(samplers.all()).thenReturn(List.of());
        controller.setSampleController(samples);
    }

    private WebConfiguration configuration() {
        WebConfiguration configuration = new WebConfiguration();
        configuration.setListenAddress("127.0.0.1");
        configuration.setPort(0);
        return configuration;
    }

    private HttpResponse<String> get(String path, String bearer, String origin) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + controller.getBoundPort() + path))
            .timeout(Duration.ofSeconds(5));
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        if (origin != null) {
            request.header("Origin", origin);
        }
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private static final class TestWebController extends WebController {
        private final Logger logger = Logger.getLogger("react-web-hotload-" + System.nanoTime());
        private final AtomicBoolean blockNextBind = new AtomicBoolean();
        private final AtomicBoolean failRelayBridge = new AtomicBoolean();
        private final CountDownLatch awaitBind = new CountDownLatch(1);
        private final CountDownLatch releaseBind = new CountDownLatch(1);
        private final CountDownLatch initialStartupFinished = new CountDownLatch(1);

        @Override
        protected void executeAsync(Runnable runnable) {
            Thread.ofVirtual().start(() -> {
                try {
                    runnable.run();
                } finally {
                    initialStartupFinished.countDown();
                }
            });
        }

        @Override
        protected int portSearchAttempts() {
            return 1;
        }

        @Override
        protected IdentityDto resolveIdentity() {
            IdentityDto identity = new IdentityDto();
            identity.version = "hotload-test";
            identity.serverName = "HotloadTest";
            identity.serverId = "127.0.0.1:0";
            return identity;
        }

        @Override
        protected Logger resolveConsoleLogger() {
            return logger;
        }

        @Override
        protected RelayLoopbackBridge createRelayLoopbackBridge(int port) {
            if (failRelayBridge.compareAndSet(true, false)) {
                throw new IllegalStateException("Relay bridge failed to start");
            }
            return super.createRelayLoopbackBridge(port);
        }

        @Override
        protected void beforeListenerBind() {
            if (!blockNextBind.compareAndSet(true, false)) {
                return;
            }
            awaitBind.countDown();
            try {
                if (!releaseBind.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to release web hotload bind");
                }
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted waiting to release web hotload bind", failure);
            }
        }
    }
}
