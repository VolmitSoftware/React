package art.arcane.react.web;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.web.RingLogHandler;
import art.arcane.react.api.web.WebConfiguration;
import art.arcane.react.api.web.dto.IdentityDto;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.core.controller.WebController;
import art.arcane.react.util.project.registry.Registry;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

class WebControllerLifecycleTest {
    private WebController controller;

    @AfterEach
    void tearDown() {
        if (controller != null) {
            controller.stop();
            controller = null;
        }
    }

    @Test
    void stopDuringStartupPreventsPublicationAndClosesLocalResources(@TempDir File dataFolder) throws Exception {
        int port = reservePort();
        Logger logger = Logger.getLogger("react-web-stop-during-start-" + System.nanoTime());
        BlockingWebController blockingController = new BlockingWebController(logger);
        controller = blockingController;
        configure(blockingController, dataFolder, port);

        blockingController.start();
        blockingController.postStart();
        Assertions.assertTrue(blockingController.awaitPushExecutorCreation(5, TimeUnit.SECONDS));

        blockingController.stop();
        Assertions.assertThrows(RuntimeException.class, () -> blockingController.awaitStart(1000L));
        blockingController.releasePushExecutorCreation();
        Assertions.assertTrue(blockingController.awaitAsyncCompletion(5, TimeUnit.SECONDS));

        assertStopped(blockingController);
        Assertions.assertEquals(0, logger.getHandlers().length);
        assertPortAvailable(port);
    }

    @Test
    void completedRuntimeCanStopAndRestartWithFreshResources(@TempDir File dataFolder) throws Exception {
        Logger logger = Logger.getLogger("react-web-restart-" + System.nanoTime());
        AsyncWebController asyncController = new AsyncWebController(logger);
        controller = asyncController;
        configure(asyncController, dataFolder, 0);

        asyncController.start();
        asyncController.postStart();
        asyncController.awaitStart(5000L);

        Javalin firstApp = asyncController.getApp();
        ExecutorService firstSendExecutor = asyncController.getWsSendExecutor();
        ScheduledExecutorService firstPushExecutor = asyncController.getWsPushExecutor();
        RingLogHandler firstLogHandler = asyncController.getLogHandler();
        int firstPort = asyncController.getBoundPort();
        assertRunning(asyncController);
        assertPing(firstPort);

        asyncController.stop();
        assertStopped(asyncController);
        Assertions.assertTrue(asyncController.pairingUnavailableReason().contains("not bound"));
        Assertions.assertThrows(IllegalStateException.class, asyncController::resolveDirectUrl);
        Assertions.assertTrue(firstSendExecutor.isShutdown());
        Assertions.assertTrue(firstPushExecutor.isShutdown());
        Assertions.assertFalse(List.of(logger.getHandlers()).contains(firstLogHandler));

        asyncController.start();
        asyncController.postStart();
        asyncController.awaitStart(5000L);

        assertRunning(asyncController);
        Assertions.assertNotSame(firstApp, asyncController.getApp());
        Assertions.assertNotSame(firstSendExecutor, asyncController.getWsSendExecutor());
        Assertions.assertNotSame(firstPushExecutor, asyncController.getWsPushExecutor());
        Assertions.assertNotSame(firstLogHandler, asyncController.getLogHandler());
        assertPing(asyncController.getBoundPort());
    }

    @Test
    void bindFailureLeavesListenerUnboundAndPairingUnavailable(@TempDir File dataFolder) throws Exception {
        Logger logger = Logger.getLogger("react-web-bind-failure-" + System.nanoTime());
        AsyncWebController asyncController = new AsyncWebController(logger);
        controller = asyncController;

        try (ServerSocket occupied = new ServerSocket()) {
            occupied.bind(new InetSocketAddress("127.0.0.1", 0));
            configure(asyncController, dataFolder, occupied.getLocalPort());

            asyncController.start();
            asyncController.postStart();
            Assertions.assertThrows(RuntimeException.class, () -> asyncController.awaitStart(5000L));

            Assertions.assertNull(asyncController.getApp());
            Assertions.assertEquals(0, asyncController.getBoundPort());
            Assertions.assertNotNull(asyncController.getStartFailure());
            Assertions.assertTrue(asyncController.pairingUnavailableReason().contains("failed to start"));
            Assertions.assertThrows(IllegalStateException.class, asyncController::resolveDirectUrl);
            Assertions.assertTrue(asyncController.getTokenStore().all().isEmpty());
            Assertions.assertFalse(asyncController.tokensFile().exists());
        }
    }

    @Test
    void startupUsesPluginClassLoaderAndRestoresWorkerContext(@TempDir File dataFolder) throws Exception {
        Logger logger = Logger.getLogger("react-web-context-loader-" + System.nanoTime());
        ClassLoader marker = new ClassLoader(null) {
        };
        ContextClassLoaderWebController contextController = new ContextClassLoaderWebController(logger, marker);
        controller = contextController;
        configure(contextController, dataFolder, 0);

        contextController.start();
        contextController.postStart();
        contextController.awaitStart(5000L);
        Assertions.assertTrue(contextController.awaitAsyncCompletion(5, TimeUnit.SECONDS));

        Assertions.assertSame(WebController.class.getClassLoader(), contextController.getStartupContextClassLoader());
        Assertions.assertSame(marker, contextController.getRestoredContextClassLoader());
    }

    private void configure(WebController target, File dataFolder, int port) {
        WebConfiguration configuration = new WebConfiguration();
        configuration.setListenerEnabled(true);
        configuration.setListenAddress("127.0.0.1");
        configuration.setPort(port);
        configuration.setRelayEnabled(false);
        target.setConfig(configuration);
        target.setDataFolder(dataFolder);
        target.setSampleController(emptySampleController());
    }

    @SuppressWarnings("unchecked")
    private SampleController emptySampleController() {
        SampleController sampleController = Mockito.mock(SampleController.class);
        Registry<Sampler> registry = Mockito.mock(Registry.class);
        Mockito.when(sampleController.getSamplers()).thenReturn(registry);
        Mockito.when(registry.all()).thenReturn(List.of());
        return sampleController;
    }

    private void assertRunning(WebController target) {
        Assertions.assertNotNull(target.getApp());
        Assertions.assertTrue(target.getBoundPort() > 0);
        Assertions.assertNotNull(target.getMetricsSessions());
        Assertions.assertNotNull(target.getMetricsBroadcaster());
        Assertions.assertNotNull(target.getWsPushExecutor());
        Assertions.assertNotNull(target.getWsSendExecutor());
        Assertions.assertNotNull(target.getLogHandler());
        Assertions.assertNotNull(target.getLogSessions());
        Assertions.assertNotNull(target.getWsAuthenticationGate());
        Assertions.assertNotNull(target.getAttachedLogger());
        Assertions.assertNull(target.getStartFailure());
    }

    private void assertStopped(WebController target) {
        Assertions.assertNull(target.getApp());
        Assertions.assertEquals(0, target.getBoundPort());
        Assertions.assertNull(target.getRelayClient());
        Assertions.assertNull(target.getMetricsSessions());
        Assertions.assertNull(target.getMetricsBroadcaster());
        Assertions.assertNull(target.getWsPushExecutor());
        Assertions.assertNull(target.getWsSendExecutor());
        Assertions.assertNull(target.getLogHandler());
        Assertions.assertNull(target.getLogSessions());
        Assertions.assertNull(target.getWsAuthenticationGate());
        Assertions.assertNull(target.getAttachedLogger());
        Assertions.assertNull(target.getLog4jConsoleCapture());
    }

    private void assertPing(int port) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/ping"))
            .GET()
            .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(
            request,
            HttpResponse.BodyHandlers.ofString()
        );
        Assertions.assertEquals(200, response.statusCode());
    }

    private int reservePort() throws IOException {
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress("127.0.0.1", 0));
            return socket.getLocalPort();
        }
    }

    private void assertPortAvailable(int port) throws IOException {
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress("127.0.0.1", port));
        }
    }

    private static class AsyncWebController extends WebController {
        private final Logger logger;

        private AsyncWebController(Logger logger) {
            this.logger = logger;
        }

        @Override
        protected void executeAsync(Runnable runnable) {
            Thread.ofVirtual().start(runnable);
        }

        @Override
        protected IdentityDto resolveIdentity() {
            IdentityDto identity = new IdentityDto();
            identity.version = "lifecycle-test";
            identity.serverName = "LifecycleTest";
            identity.folia = false;
            identity.serverId = "127.0.0.1:0";
            return identity;
        }

        @Override
        protected Logger resolveConsoleLogger() {
            return logger;
        }
    }

    private static final class BlockingWebController extends AsyncWebController {
        private final CountDownLatch pushExecutorCreation = new CountDownLatch(1);
        private final CountDownLatch releasePushExecutorCreation = new CountDownLatch(1);
        private final CountDownLatch asyncCompletion = new CountDownLatch(1);

        private BlockingWebController(Logger logger) {
            super(logger);
        }

        @Override
        protected void executeAsync(Runnable runnable) {
            Thread.ofVirtual().start(() -> {
                try {
                    runnable.run();
                } finally {
                    asyncCompletion.countDown();
                }
            });
        }

        @Override
        protected ScheduledExecutorService createWsPushExecutor() {
            pushExecutorCreation.countDown();
            try {
                if (!releasePushExecutorCreation.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to release WebController startup");
                }
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting to release WebController startup", failure);
            }
            return super.createWsPushExecutor();
        }

        private boolean awaitPushExecutorCreation(long timeout, TimeUnit unit) throws InterruptedException {
            return pushExecutorCreation.await(timeout, unit);
        }

        private void releasePushExecutorCreation() {
            releasePushExecutorCreation.countDown();
        }

        private boolean awaitAsyncCompletion(long timeout, TimeUnit unit) throws InterruptedException {
            return asyncCompletion.await(timeout, unit);
        }
    }

    private static final class ContextClassLoaderWebController extends AsyncWebController {
        private final ClassLoader marker;
        private final AtomicReference<ClassLoader> startupContextClassLoader = new AtomicReference<>();
        private final AtomicReference<ClassLoader> restoredContextClassLoader = new AtomicReference<>();
        private final CountDownLatch asyncCompletion = new CountDownLatch(1);

        private ContextClassLoaderWebController(Logger logger, ClassLoader marker) {
            super(logger);
            this.marker = marker;
        }

        @Override
        protected void executeAsync(Runnable runnable) {
            Thread.ofVirtual().start(() -> {
                Thread thread = Thread.currentThread();
                thread.setContextClassLoader(marker);
                try {
                    runnable.run();
                } finally {
                    restoredContextClassLoader.set(thread.getContextClassLoader());
                    asyncCompletion.countDown();
                }
            });
        }

        @Override
        protected ScheduledExecutorService createWsPushExecutor() {
            startupContextClassLoader.set(Thread.currentThread().getContextClassLoader());
            return super.createWsPushExecutor();
        }

        private boolean awaitAsyncCompletion(long timeout, TimeUnit unit) throws InterruptedException {
            return asyncCompletion.await(timeout, unit);
        }

        private ClassLoader getStartupContextClassLoader() {
            return startupContextClassLoader.get();
        }

        private ClassLoader getRestoredContextClassLoader() {
            return restoredContextClassLoader.get();
        }
    }
}
